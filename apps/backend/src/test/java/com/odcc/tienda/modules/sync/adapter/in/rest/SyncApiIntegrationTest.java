package com.odcc.tienda.modules.sync.adapter.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.sync.application.port.out.SyncRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SyncApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SyncRepositoryPort syncRepository;

    @Test
    void shouldAuthorizeOnlyTheUserBoundToTheDevice() {
        Fixture fixture = fixture();

        assertTrue(syncRepository.userOwnsDevice(fixture.userId(), fixture.deviceId()));
        assertFalse(syncRepository.userOwnsDevice(UUID.randomUUID(), fixture.deviceId()));
    }

    @Test
    void shouldRejectInactiveBranchGrant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID userId = UUID.randomUUID();
        UUID branchId = insertBranch("SYNC-INACTIVE-GRANT");
        jdbc.update("""
            INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """, userId, "sync_inactive_" + suffix, passwordEncoder.encode("correct-password"), "Sync Inactive");
        jdbc.update("""
            INSERT INTO iam.user_branch_access (user_id, branch_id, status)
            VALUES (?, ?, 'INACTIVE')
            """, userId, branchId);

        assertFalse(syncRepository.userCanAccessBranch(userId, branchId));
    }

    @Test
    void shouldProcessOfflineCartCountOutboxCheckpointAndConflict() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");

        UUID cartOperationId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID cartKey = UUID.randomUUID();
        String cartPayload = """
            {
              "operationId":"%s",
              "deviceId":"%s",
              "deviceSequence":1,
              "idempotencyKey":"%s",
              "operationType":"CART_UPSERT",
              "aggregateType":"CART",
              "aggregateId":"%s",
              "clientCreatedAt":"2026-07-29T06:00:00Z",
              "payload":{
                "branchId":"%s",
                "currencyCode":"MXN",
                "expiresAt":"2030-01-01T00:00:00Z",
                "items":[{
                  "productPresentationId":"%s",
                  "quantity":2,
                  "unitPriceSnapshot":25.50
                }]
              }
            }
            """.formatted(cartOperationId, fixture.deviceId(), cartKey, cartId,
            fixture.branchId(), fixture.presentationId());

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-ID", "sync-cart-flow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cartPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SYNC_OPERATION_RECEIVED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").value("sync-cart-flow"))
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.result.cartId").value(cartId.toString()));

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cartPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.operationId").value(cartOperationId.toString()))
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        UUID unsupportedOperationId = UUID.randomUUID();
        String unsupportedPayload = envelope(
            unsupportedOperationId, fixture.deviceId(), 2, UUID.randomUUID(),
            "SALES_ORDER_CREATE", "SALES_ORDER", null, "{}"
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(unsupportedPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SYNC_PAYLOAD_INVALID"));
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM sync.inbox_operations WHERE operation_id = ?",
            Integer.class,
            unsupportedOperationId
        ));

        String countPayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 2, UUID.randomUUID(),
            "INVENTORY_COUNT_CREATE", "INVENTORY_COUNT", null,
            """
                {
                  "warehouseId":"%s",
                  "items":[{
                    "productPresentationId":"%s",
                    "lotId":null,
                    "countedQuantity":5
                  }]
                }
                """.formatted(fixture.warehouseId(), fixture.presentationId())
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(countPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.data.result.status").value("DRAFT"));

        UUID gapOperationId = UUID.randomUUID();
        String gapPayload = envelope(
            gapOperationId, fixture.deviceId(), 4, UUID.randomUUID(),
            "CART_UPSERT", "CART", UUID.randomUUID(),
            """
                {
                  "branchId":"%s",
                  "currencyCode":"MXN",
                  "expiresAt":"2030-01-01T00:00:00Z",
                  "items":[{
                    "productPresentationId":"%s",
                    "quantity":1,
                    "unitPriceSnapshot":25.50
                  }]
                }
                """.formatted(fixture.branchId(), fixture.presentationId())
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gapPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("CONFLICT"))
            .andExpect(jsonPath("$.data.errorCode").value("SEQUENCE_GAP"));

        String sequenceThreePayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 3, UUID.randomUUID(),
            "CART_UPSERT", "CART", UUID.randomUUID(),
            """
                {
                  "branchId":"%s",
                  "currencyCode":"MXN",
                  "expiresAt":"2030-01-01T00:00:00Z",
                  "items":[{
                    "productPresentationId":"%s",
                    "quantity":1,
                    "unitPriceSnapshot":25.50
                  }]
                }
                """.formatted(fixture.branchId(), fixture.presentationId())
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sequenceThreePayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        MvcResult conflictsResult = mockMvc.perform(get("/api/v1/sync/conflicts")
                .param("deviceId", fixture.deviceId().toString())
                .param("resolved", "false")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].conflictType").value("SEQUENCE_GAP"))
            .andReturn();
        String conflictId = JsonPath.read(conflictsResult.getResponse().getContentAsString(), "$.data[0].conflictId");

        mockMvc.perform(post("/api/v1/sync/conflicts/{conflictId}/resolve", conflictId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"SERVER_WINS\",\"resolutionNotes\":\"Se conserva estado servidor\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resolution").value("SERVER_WINS"))
            .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());

        MvcResult outboxResult = mockMvc.perform(get("/api/v1/sync/outbox")
                .param("deviceId", fixture.deviceId().toString())
                .param("afterSequence", "0")
                .param("limit", "100")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SYNC_OUTBOX_FOUND"))
            .andExpect(jsonPath("$.data.events").isArray())
            .andReturn();
        Number nextSequence = JsonPath.read(outboxResult.getResponse().getContentAsString(), "$.data.nextSequence");

        mockMvc.perform(post("/api/v1/sync/devices/{deviceId}/checkpoint", fixture.deviceId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outboxSequence\":%d}".formatted(nextSequence.longValue())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastAcknowledgedOutboxSequence").value(nextSequence.longValue()))
            .andExpect(jsonPath("$.data.lastProcessedSequence").value(4));

        Integer carts = jdbc.queryForObject("SELECT COUNT(*) FROM sales.carts WHERE cart_id = ?", Integer.class, cartId);
        Integer counts = jdbc.queryForObject("SELECT COUNT(*) FROM inventory.inventory_counts WHERE warehouse_id = ?", Integer.class, fixture.warehouseId());
        assertEquals(1, carts);
        assertEquals(1, counts);
    }

    @Test
    void shouldRejectChangedIdempotentPayloadAndUnauthorizedRequest() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        UUID operationId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        String originalPayload = """
            {
              "branchId":"%s",
              "currencyCode":"MXN",
              "expiresAt":"2030-01-01T00:00:00Z",
              "items":[{"productPresentationId":"%s","quantity":1,"unitPriceSnapshot":25.50}]
            }
            """.formatted(fixture.branchId(), fixture.presentationId());
        String original = envelope(operationId, fixture.deviceId(), 1, idempotencyKey,
            "CART_UPSERT", "CART", cartId, originalPayload);
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(original))
            .andExpect(status().isCreated());

        String changedPayload = originalPayload.replace("\"quantity\":1", "\"quantity\":2");
        String changed = envelope(UUID.randomUUID(), fixture.deviceId(), 2, idempotencyKey,
            "CART_UPSERT", "CART", cartId, changedPayload);
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(changed))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_IDEMPOTENCY_CONFLICT"))
            .andExpect(jsonPath("$.reason").value("Conflict"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(get("/api/v1/sync/outbox").param("deviceId", fixture.deviceId().toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectOversizedAndDeepPayloads() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");

        String oversized = "{\"padding\":\"" + "a".repeat(256 * 1024) + "\"}";
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(oversized))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("SYNC_PAYLOAD_TOO_LARGE"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        String nested = "\"value\"";
        for (int level = 0; level < 21; level++) nested = "{\"nested\":" + nested + "}";
        String deepPayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 1, UUID.randomUUID(),
            "CART_UPSERT", "CART", UUID.randomUUID(), nested
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(deepPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SYNC_PAYLOAD_INVALID"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void shouldRejectCartUpsertFromAnotherBranchWithoutChangingOwnerOrItems() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        UUID ownerBranchId = insertBranch("CART-OWNER-BRANCH");
        UUID ownerDeviceId = insertDevice(ownerBranchId, "CART-OWNER-DEVICE");
        UUID cartId = insertCart(ownerBranchId, ownerDeviceId, "ACTIVE", fixture.presentationId());

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cartEnvelope(fixture, cartId, new BigDecimal("9"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertEquals(ownerBranchId, jdbc.queryForObject(
            "SELECT branch_id FROM sales.carts WHERE cart_id = ?", UUID.class, cartId
        ));
        assertEquals(ownerDeviceId, jdbc.queryForObject(
            "SELECT device_id FROM sales.carts WHERE cart_id = ?", UUID.class, cartId
        ));
        assertCartItemQuantity(cartId, "1.000");
    }

    @Test
    void shouldRejectCartUpsertFromAnotherDeviceWithoutChangingItems() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        UUID ownerDeviceId = insertDevice(fixture.branchId(), "CART-OWNER-DEVICE");
        UUID cartId = insertCart(fixture.branchId(), ownerDeviceId, "ACTIVE", fixture.presentationId());

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cartEnvelope(fixture, cartId, new BigDecimal("9"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertEquals(ownerDeviceId, jdbc.queryForObject(
            "SELECT device_id FROM sales.carts WHERE cart_id = ?", UUID.class, cartId
        ));
        assertCartItemQuantity(cartId, "1.000");
    }

    @Test
    void shouldRejectCartUpsertWhenActorSpoofsTheOwningDeviceId() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        UUID otherDeviceId = insertDevice(fixture.branchId(), "CART-OTHER-DEVICE");
        UUID cartId = insertCart(fixture.branchId(), otherDeviceId, "ACTIVE", fixture.presentationId());
        String spoofedEnvelope = envelope(
            UUID.randomUUID(), otherDeviceId, 1, UUID.randomUUID(),
            "CART_UPSERT", "CART", cartId,
            """
                {
                  "branchId":"%s",
                  "currencyCode":"MXN",
                  "expiresAt":"2030-01-01T00:00:00Z",
                  "items":[{
                    "productPresentationId":"%s",
                    "quantity":9,
                    "unitPriceSnapshot":25.50
                  }]
                }
                """.formatted(fixture.branchId(), fixture.presentationId())
        );

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(spoofedEnvelope))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertCartItemQuantity(cartId, "1.000");
    }

    @Test
    void shouldRejectCartUpsertInTerminalStatusWithoutChangingItems() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        UUID cartId = insertCart(fixture.branchId(), fixture.deviceId(), "CONVERTED", fixture.presentationId());

        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cartEnvelope(fixture, cartId, new BigDecimal("9"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SYNC_CONFLICT"));

        assertEquals("CONVERTED", jdbc.queryForObject(
            "SELECT status FROM sales.carts WHERE cart_id = ?", String.class, cartId
        ));
        assertCartItemQuantity(cartId, "1.000");
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID userId = UUID.randomUUID();
        String username = "sync_" + suffix;
        jdbc.update("""
            INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """, userId, username, passwordEncoder.encode("correct-password"), "Sync Test");
        jdbc.update("""
            INSERT INTO iam.user_roles (user_id, role_id)
            SELECT ?, role_id FROM iam.roles WHERE code = 'SYSTEM_ADMIN'
            """, userId);

        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization.branches (branch_id, code, name) VALUES (?, ?, ?)",
            branchId, "SYNC-" + suffix, "Sucursal Sync");
        jdbc.update("INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name) VALUES (?, ?, ?, ?)",
            warehouseId, branchId, "WH-" + suffix, "Almacen Sync");
        jdbc.update("""
            INSERT INTO organization.devices (
                device_id, branch_id, warehouse_id, device_code, device_type, status
            ) VALUES (?, ?, ?, ?, 'MOBILE_EMPLOYEE', 'ACTIVE')
            """, deviceId, branchId, warehouseId, "DEV-" + suffix);
        jdbc.update(
            "INSERT INTO sync.device_user_bindings (device_id, user_id) VALUES (?, ?)",
            deviceId,
            userId
        );
        jdbc.update("INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol) VALUES (?, ?, ?, ?)",
            unitId, "US-" + suffix, "Pieza Sync", "pza");
        jdbc.update("""
            INSERT INTO catalog.products (
                product_id, name, product_type, tracks_inventory, tracks_lots, tracks_expiration, status
            ) VALUES (?, ?, 'GOODS', TRUE, FALSE, FALSE, 'ACTIVE')
            """, productId, "Producto Sync");
        jdbc.update("""
            INSERT INTO catalog.product_presentations (
                product_presentation_id, product_id, unit_id, sku, name, status
            ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """, presentationId, productId, unitId, "SYNC-SKU-" + suffix, "Presentacion Sync");
        jdbc.update("""
            INSERT INTO inventory.stock_balances (
                stock_balance_id, warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost
            ) VALUES (?, ?, ?, 5, 10)
            """, UUID.randomUUID(), warehouseId, presentationId);
        return new Fixture(userId, username, branchId, warehouseId, deviceId, presentationId);
    }

    private UUID insertBranch(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID branchId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization.branches (branch_id, code, name) VALUES (?, ?, ?)",
            branchId, prefix + "-" + suffix, prefix);
        return branchId;
    }

    private UUID insertDevice(UUID branchId, String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID deviceId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO organization.devices (device_id, branch_id, device_code, device_type, status)
            VALUES (?, ?, ?, 'MOBILE_EMPLOYEE', 'ACTIVE')
            """, deviceId, branchId, prefix + "-" + suffix);
        return deviceId;
    }

    private UUID insertCart(UUID branchId, UUID deviceId, String status, UUID presentationId) {
        UUID cartId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO sales.carts (cart_id, branch_id, device_id, status, currency_code)
            VALUES (?, ?, ?, ?, 'MXN')
            """, cartId, branchId, deviceId, status);
        jdbc.update("""
            INSERT INTO sales.cart_items (
                cart_item_id, cart_id, product_presentation_id, quantity, unit_price_snapshot
            ) VALUES (?, ?, ?, 1, 10)
            """, UUID.randomUUID(), cartId, presentationId);
        return cartId;
    }

    private String cartEnvelope(Fixture fixture, UUID cartId, BigDecimal quantity) {
        return envelope(
            UUID.randomUUID(), fixture.deviceId(), 1, UUID.randomUUID(),
            "CART_UPSERT", "CART", cartId,
            """
                {
                  "branchId":"%s",
                  "currencyCode":"MXN",
                  "expiresAt":"2030-01-01T00:00:00Z",
                  "items":[{
                    "productPresentationId":"%s",
                    "quantity":%s,
                    "unitPriceSnapshot":25.50
                  }]
                }
                """.formatted(fixture.branchId(), fixture.presentationId(), quantity.toPlainString())
        );
    }

    private void assertCartItemQuantity(UUID cartId, String expected) {
        BigDecimal quantity = jdbc.queryForObject(
            "SELECT quantity FROM sales.cart_items WHERE cart_id = ?", BigDecimal.class, cartId
        );
        assertEquals(0, new BigDecimal(expected).compareTo(quantity));
    }

    private String envelope(
        UUID operationId,
        UUID deviceId,
        long sequence,
        UUID idempotencyKey,
        String operationType,
        String aggregateType,
        UUID aggregateId,
        String payload
    ) {
        return """
            {
              "operationId":"%s",
              "deviceId":"%s",
              "deviceSequence":%d,
              "idempotencyKey":"%s",
              "operationType":"%s",
              "aggregateType":"%s",
              "aggregateId":%s,
              "payload":%s,
              "clientCreatedAt":"2026-07-29T06:00:00Z"
            }
            """.formatted(operationId, deviceId, sequence, idempotencyKey, operationType, aggregateType,
            aggregateId == null ? "null" : "\"" + aggregateId + "\"", payload);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private record Fixture(
        UUID userId,
        String username,
        UUID branchId,
        UUID warehouseId,
        UUID deviceId,
        UUID presentationId
    ) {
    }
}
