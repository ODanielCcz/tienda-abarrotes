package com.odcc.tienda.modules.inventory.adapter.in.rest;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
class AdvancedInventoryApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String CORRELATION_ID = "advanced-inventory-flow-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldAdjustTransferReserveReleaseCountAndFindExpiringLots() throws Exception {
        UUID userId = insertUser("advanced_inventory_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("advanced_inventory_admin", "correct-password");
        TestContext context = createContext("ADV-INV");
        ProductFixture product = insertProductPresentation("ADV-INV");
        insertStock(context.warehouseId(), product.presentationId(), product.lotId(), new BigDecimal("10.000"));

        mockMvc.perform(
                post("/api/v1/inventory/adjustments")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", CORRELATION_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "reason": "Entrada por ajuste de prueba",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "direction": "IN",
                              "quantity": 5,
                              "unitCost": 12.50
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INVENTORY_ADJUSTMENT_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
            .andExpect(jsonPath("$.data.movementType").value("ADJUSTMENT_IN"))
            .andExpect(jsonPath("$.data.items[0].direction").value("IN"));
        assertEquals(new BigDecimal("15.000"), stockOnHand(context.warehouseId(), product.presentationId()));
        assertMovementCount(context.warehouseId(), "ADJUSTMENT_IN", 1);

        mockMvc.perform(
                post("/api/v1/inventory/transfers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fromWarehouseId": "%s",
                          "toWarehouseId": "%s",
                          "reason": "Traspaso a bodega secundaria",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 3,
                              "unitCost": 12.50
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), context.secondWarehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INVENTORY_TRANSFER_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data[0].movementType").value("TRANSFER_OUT"))
            .andExpect(jsonPath("$.data[1].movementType").value("TRANSFER_IN"));
        assertEquals(new BigDecimal("12.000"), stockOnHand(context.warehouseId(), product.presentationId()));
        assertEquals(new BigDecimal("3.000"), stockOnHand(context.secondWarehouseId(), product.presentationId()));
        assertMovementCount(context.warehouseId(), "TRANSFER_OUT", 1);
        assertMovementCount(context.secondWarehouseId(), "TRANSFER_IN", 1);

        MvcResult reservationResult = mockMvc.perform(
                post("/api/v1/inventory/reservations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sourceType": "ORDER",
                          "sourceId": "%s",
                          "idempotencyKey": "%s",
                          "expiresAt": "%s",
                          "items": [
                            {
                              "warehouseId": "%s",
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 2
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600), context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INVENTORY_RESERVATION_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn();
        String reservationId = JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.data.reservationId");
        assertEquals(new BigDecimal("2.000"), stockReserved(context.warehouseId(), product.presentationId()));
        assertMovementCount(context.warehouseId(), "RESERVATION", 1);

        mockMvc.perform(
                post("/api/v1/inventory/reservations/{reservationId}/release", reservationId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVENTORY_RESERVATION_RELEASED"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertEquals(new BigDecimal("0.000"), stockReserved(context.warehouseId(), product.presentationId()));
        assertMovementCount(context.warehouseId(), "RESERVATION_RELEASE", 1);

        MvcResult countResult = mockMvc.perform(
                post("/api/v1/inventory/counts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "countedQuantity": 11
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("INVENTORY_COUNT_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.items[0].expectedQuantity").value(12.000))
            .andReturn();
        String countId = JsonPath.read(countResult.getResponse().getContentAsString(), "$.data.inventoryCountId");

        mockMvc.perform(
                post("/api/v1/inventory/counts/{inventoryCountId}/confirm", countId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVENTORY_COUNT_CONFIRMED"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertEquals(new BigDecimal("11.000"), stockOnHand(context.warehouseId(), product.presentationId()));
        assertMovementCount(context.warehouseId(), "ADJUSTMENT_IN", 2);

        mockMvc.perform(
                get("/api/v1/inventory/expiring-lots")
                    .param("expiresBefore", LocalDate.now().plusDays(10).toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("INVENTORY_EXPIRING_LOTS_FOUND"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data[?(@.lotId == '%s')]".formatted(product.lotId())).exists());

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN (
                      'INVENTORY_ADJUSTMENT_CREATED',
                      'INVENTORY_TRANSFER_CREATED',
                      'INVENTORY_RESERVATION_CREATED',
                      'INVENTORY_RESERVATION_RELEASED',
                      'INVENTORY_COUNT_CREATED',
                      'INVENTORY_COUNT_CONFIRMED'
                  )
                """,
            Integer.class,
            userId
        );
        assertEquals(6, auditCount);
    }

    @Test
    void shouldRejectInvalidAdvancedInventoryOperations() throws Exception {
        insertUser("advanced_inventory_rules", "correct-password", "SYSTEM_ADMIN");
        String token = login("advanced_inventory_rules", "correct-password");
        TestContext context = createContext("INV-RULE");
        ProductFixture product = insertProductPresentation("INV-RULE");
        insertStock(context.warehouseId(), product.presentationId(), product.lotId(), new BigDecimal("5.000"));

        mockMvc.perform(
                post("/api/v1/inventory/adjustments")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "direction": "IN",
                              "quantity": 0
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), product.presentationId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        mockMvc.perform(
                post("/api/v1/inventory/transfers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fromWarehouseId": "%s",
                          "toWarehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_RECEIPT"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        mockMvc.perform(
                post("/api/v1/inventory/transfers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "fromWarehouseId": "%s",
                          "toWarehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 99
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), context.secondWarehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_RECEIPT"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        mockMvc.perform(
                post("/api/v1/inventory/reservations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sourceType": "ORDER",
                          "sourceId": "%s",
                          "idempotencyKey": "%s",
                          "expiresAt": "%s",
                          "items": [
                            {
                              "warehouseId": "%s",
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 99
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600), context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_RECEIPT"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        MvcResult reservationResult = mockMvc.perform(
                post("/api/v1/inventory/reservations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "sourceType": "ORDER",
                          "sourceId": "%s",
                          "idempotencyKey": "%s",
                          "expiresAt": "%s",
                          "items": [
                            {
                              "warehouseId": "%s",
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(3600), context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        String reservationId = JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.data.reservationId");

        mockMvc.perform(post("/api/v1/inventory/reservations/{reservationId}/release", reservationId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/reservations/{reservationId}/release", reservationId).header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_RECEIPT"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        MvcResult countResult = mockMvc.perform(
                post("/api/v1/inventory/counts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "lotId": "%s",
                              "countedQuantity": 5
                            }
                          ]
                        }
                        """.formatted(context.warehouseId(), product.presentationId(), product.lotId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        String countId = JsonPath.read(countResult.getResponse().getContentAsString(), "$.data.inventoryCountId");

        mockMvc.perform(post("/api/v1/inventory/counts/{inventoryCountId}/confirm", countId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/inventory/counts/{inventoryCountId}/confirm", countId).header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INVENTORY_RECEIPT"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void shouldProtectAdvancedInventoryEndpointsAndExposeOpenApi() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/adjustments"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("Unauthorized"))
            .andExpect(jsonPath("$.correlationId").exists());

        createRoleWithoutPermissions("TEST_NO_ADV_INV");
        insertUser("no_advanced_inventory", "correct-password", "TEST_NO_ADV_INV");
        String noPermissionToken = login("no_advanced_inventory", "correct-password");

        mockMvc.perform(
                post("/api/v1/inventory/adjustments")
                    .header("Authorization", "Bearer " + noPermissionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "direction": "IN",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("Forbidden"))
            .andExpect(jsonPath("$.correlationId").exists());

        insertUser("openapi_advanced_inventory", "correct-password", "SYSTEM_ADMIN");
        String token = login("openapi_advanced_inventory", "correct-password");

        mockMvc.perform(
                get("/v3/api-docs")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/adjustments']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/transfers']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/counts']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/counts/{inventoryCountId}/confirm']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/reservations']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/reservations/{reservationId}/release']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/inventory/expiring-lots']").exists());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                post(LOGIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password))
            )
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private UUID insertUser(String username, String password, String roleCode) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            userId,
            username,
            passwordEncoder.encode(password),
            "Advanced Inventory Test " + username
        );
        jdbcTemplate.update(
            """
                INSERT INTO iam.user_roles (user_id, role_id)
                SELECT ?, role_id FROM iam.roles WHERE code = ?
                """,
            userId,
            roleCode
        );
        return userId;
    }

    private void createRoleWithoutPermissions(String roleCode) {
        jdbcTemplate.update(
            """
                INSERT INTO iam.roles (code, name, description, is_system)
                VALUES (?, ?, 'Rol de prueba sin permisos de inventario avanzado', FALSE)
                """,
            roleCode,
            roleCode
        );
    }

    private TestContext createContext(String prefix) {
        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID secondWarehouseId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbcTemplate.update(
            "INSERT INTO organization.branches (branch_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
            branchId,
            code(prefix, "B", suffix),
            prefix + " Branch"
        );
        jdbcTemplate.update(
            "INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            warehouseId,
            branchId,
            code(prefix, "W1", suffix),
            prefix + " Warehouse 1"
        );
        jdbcTemplate.update(
            "INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            secondWarehouseId,
            branchId,
            code(prefix, "W2", suffix),
            prefix + " Warehouse 2"
        );
        return new TestContext(branchId, warehouseId, secondWarehouseId);
    }

    private ProductFixture insertProductPresentation(String prefix) {
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbcTemplate.update("INSERT INTO catalog.categories (category_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", categoryId, code(prefix, "CAT", suffix), prefix + " Categoria");
        jdbcTemplate.update("INSERT INTO catalog.brands (brand_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", brandId, code(prefix, "BR", suffix), prefix + " Marca");
        jdbcTemplate.update("INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol, quantity_scale) VALUES (?, ?, ?, ?, 0)", unitId, code(prefix, "UN", suffix), prefix + " Unidad", "pz");
        jdbcTemplate.update("INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, ?, 0.160000, 'ACTIVE')", taxId, code(prefix, "TX", suffix), "IVA " + suffix);
        jdbcTemplate.update(
            """
                INSERT INTO catalog.products (product_id, category_id, brand_id, name, description, product_type, tracks_inventory, tracks_lots, tracks_expiration, status)
                VALUES (?, ?, ?, ?, ?, 'GOODS', TRUE, TRUE, TRUE, 'ACTIVE')
                """,
            productId,
            categoryId,
            brandId,
            prefix + " Producto",
            "Producto de prueba para inventario avanzado"
        );
        jdbcTemplate.update(
            """
                INSERT INTO catalog.product_presentations (product_presentation_id, product_id, unit_id, tax_id, sku, name, conversion_factor, net_content, minimum_stock, status)
                VALUES (?, ?, ?, ?, ?, ?, 1, 1, 1, 'ACTIVE')
                """,
            presentationId,
            productId,
            unitId,
            taxId,
            code(prefix, "SKU", suffix),
            prefix + " Presentacion"
        );
        jdbcTemplate.update(
            """
                INSERT INTO inventory.lots (lot_id, product_presentation_id, lot_number, manufactured_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - INTERVAL '1 day', CURRENT_DATE + INTERVAL '5 days', 'ACTIVE')
                """,
            lotId,
            presentationId,
            code(prefix, "LOT", suffix)
        );
        return new ProductFixture(presentationId, lotId);
    }

    private void insertStock(UUID warehouseId, UUID presentationId, UUID lotId, BigDecimal quantity) {
        jdbcTemplate.update(
            "INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost) VALUES (?, ?, ?, 12.5000)",
            warehouseId,
            presentationId,
            quantity
        );
        jdbcTemplate.update(
            "INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity) VALUES (?, ?, ?)",
            warehouseId,
            lotId,
            quantity
        );
    }

    private BigDecimal stockOnHand(UUID warehouseId, UUID presentationId) {
        return jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            warehouseId,
            presentationId
        );
    }

    private BigDecimal stockReserved(UUID warehouseId, UUID presentationId) {
        return jdbcTemplate.queryForObject(
            "SELECT reserved_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            warehouseId,
            presentationId
        );
    }

    private void assertMovementCount(UUID warehouseId, String movementType, int expectedCount) {
        Integer movementCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM inventory.stock_movements
                WHERE warehouse_id = ?
                  AND movement_type = ?
                """,
            Integer.class,
            warehouseId,
            movementType
        );
        assertEquals(expectedCount, movementCount);
    }

    private String code(String prefix, String type, String suffix) {
        String compactPrefix = prefix.replace("-", "");
        if (compactPrefix.length() > 8) {
            compactPrefix = compactPrefix.substring(0, 8);
        }
        return compactPrefix + type + suffix;
    }

    private record TestContext(UUID branchId, UUID warehouseId, UUID secondWarehouseId) {
    }

    private record ProductFixture(UUID presentationId, UUID lotId) {
    }
}
