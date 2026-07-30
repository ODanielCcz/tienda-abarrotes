package com.odcc.tienda.modules.sync.adapter.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.odcc.tienda.TestcontainersConfiguration;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        String unsupportedPayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 2, UUID.randomUUID(),
            "SALES_ORDER_CREATE", "SALES_ORDER", null, "{}"
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(unsupportedPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.errorCode").value("SYNC_OPERATION_UNSUPPORTED"));

        String countPayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 3, UUID.randomUUID(),
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
            gapOperationId, fixture.deviceId(), 5, UUID.randomUUID(),
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

        String sequenceFourPayload = envelope(
            UUID.randomUUID(), fixture.deviceId(), 4, UUID.randomUUID(),
            "UNSUPPORTED", "TEST", null, "{}"
        );
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(sequenceFourPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

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
            .andExpect(jsonPath("$.data.lastProcessedSequence").value(5));

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
        String original = envelope(operationId, fixture.deviceId(), 1, idempotencyKey,
            "UNSUPPORTED", "TEST", null, "{}");
        mockMvc.perform(post("/api/v1/sync/inbox")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(original))
            .andExpect(status().isCreated());

        String changed = envelope(UUID.randomUUID(), fixture.deviceId(), 2, idempotencyKey,
            "UNSUPPORTED_CHANGED", "TEST", null, "{}");
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
        return new Fixture(username, branchId, warehouseId, deviceId, presentationId);
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
        String username,
        UUID branchId,
        UUID warehouseId,
        UUID deviceId,
        UUID presentationId
    ) {
    }
}
