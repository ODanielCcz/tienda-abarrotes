package com.odcc.tienda.modules.sales.adapter.in.rest;

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
class SalesReturnApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateConfirmReturnRestoreLotStockAndRegisterCashRefund() throws Exception {
        UUID userId = insertUser("sales_return_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("sales_return_admin", "correct-password");
        TestContext context = createContext("RETURN-FLOW");
        UUID presentationId = insertProductPresentation("RETURN-FLOW");
        UUID lotId = insertLot(presentationId, "LOT-RETURN-FLOW");
        insertStock(context.warehouseId(), presentationId, lotId, new BigDecimal("3.000"));
        SaleFixture sale = insertSale(context, presentationId, lotId, new BigDecimal("2.000"), new BigDecimal("42.9200"), "PAID");
        UUID paymentId = insertCapturedCashPayment(sale.salesOrderId(), new BigDecimal("42.9200"));
        UUID cashSessionId = insertOpenCashSession(context.cashRegisterId(), userId, new BigDecimal("500.0000"));

        MvcResult created = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "return-flow-create-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Cliente devolvio producto cerrado",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 2
                            }
                          ]
                        }
                        """.formatted(sale.salesOrderItemId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").value("return-flow-create-123"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.total").value(42.9200))
            .andReturn();

        String returnId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.returnId");

        mockMvc.perform(
                post("/api/v1/sales/returns/{returnId}/confirm", returnId)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "return-flow-create-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashSessionId": "%s"
                        }
                        """.formatted(cashSessionId))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_CONFIRMED"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(
                get("/api/v1/sales/returns/{returnId}", returnId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_FOUND"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.items[0].lotId").value(lotId.toString()));

        assertEquals(new BigDecimal("5.000"), stockOnHand(context.warehouseId(), presentationId));
        assertEquals(new BigDecimal("5.000"), lotOnHand(context.warehouseId(), lotId));

        String orderStatus = jdbcTemplate.queryForObject("SELECT status FROM sales.sales_orders WHERE sales_order_id = ?", String.class, sale.salesOrderId());
        String paymentStatus = jdbcTemplate.queryForObject("SELECT payment_status FROM sales.sales_orders WHERE sales_order_id = ?", String.class, sale.salesOrderId());
        assertEquals("RETURNED", orderStatus);
        assertEquals("REFUNDED", paymentStatus);

        Integer saleReturnMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE source_id = ? AND movement_type = 'SALE_RETURN'",
            Integer.class,
            UUID.fromString(returnId)
        );
        assertEquals(1, saleReturnMovements);

        Integer refundMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cash.cash_movements WHERE cash_session_id = ? AND payment_id = ? AND movement_type = 'REFUND' AND direction = 'OUT' AND amount = 42.9200",
            Integer.class,
            cashSessionId,
            paymentId
        );
        assertEquals(1, refundMovements);

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN ('SALES_RETURN_CREATED', 'SALES_RETURN_CONFIRMED')
                """,
            Integer.class,
            userId
        );
        assertEquals(2, auditCount);

        mockMvc.perform(
                post("/api/v1/sales/returns/{returnId}/confirm", returnId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashSessionId": "%s"
                        }
                        """.formatted(cashSessionId))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_ALREADY_PROCESSED"))
            .andExpect(jsonPath("$.reason").value("Conflict"))
            .andExpect(jsonPath("$.correlationId").exists());

        mockMvc.perform(
                post("/api/v1/sales/returns/{returnId}/cancel", returnId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_ALREADY_PROCESSED"))
            .andExpect(jsonPath("$.reason").value("Conflict"))
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void shouldRefundOnlyTheCashPortionOfAMixedPaymentSale() throws Exception {
        UUID userId = insertUser("mixed_return_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("mixed_return_admin", "correct-password");
        TestContext context = createContext("MIXED-RETURN");
        UUID presentationId = insertProductPresentation("MIXED-RETURN");
        UUID lotId = insertLot(presentationId, "LOT-MIXED-RETURN");
        insertStock(context.warehouseId(), presentationId, lotId, new BigDecimal("1.000"));
        SaleFixture sale = insertSale(context, presentationId, lotId, BigDecimal.ONE, new BigDecimal("100.0000"), "PAID");
        UUID cashPaymentId = insertCapturedPayment(sale.salesOrderId(), "CASH", new BigDecimal("10.0000"));
        UUID cardPaymentId = insertCapturedPayment(sale.salesOrderId(), "CARD", new BigDecimal("90.0000"));
        UUID cashSessionId = insertOpenCashSession(context.cashRegisterId(), userId, new BigDecimal("500.0000"));

        MvcResult created = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Devolucion de venta con pago mixto",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(sale.salesOrderItemId()))
            )
            .andExpect(status().isCreated())
            .andReturn();

        String returnId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.returnId");

        mockMvc.perform(
                post("/api/v1/sales/returns/{returnId}/confirm", returnId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashSessionId": "%s"
                        }
                        """.formatted(cashSessionId))
            )
            .andExpect(status().isOk());

        BigDecimal cashRefund = jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount), 0)
                FROM cash.cash_movements
                WHERE payment_id = ?
                  AND movement_type = 'REFUND'
                  AND direction = 'OUT'
                """,
            BigDecimal.class,
            cashPaymentId
        );
        Integer cardCashMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cash.cash_movements WHERE payment_id = ? AND movement_type = 'REFUND'",
            Integer.class,
            cardPaymentId
        );

        assertEquals(new BigDecimal("10.0000"), cashRefund);
        assertEquals(0, cardCashMovements);
    }

    @Test
    void shouldRejectReturnQuantityGreaterThanSoldAndProtectEndpoints() throws Exception {
        TestContext context = createContext("RETURN-SECURITY");
        UUID presentationId = insertProductPresentation("RETURN-SECURITY");
        SaleFixture sale = insertSale(context, presentationId, null, new BigDecimal("1.000"), new BigDecimal("20.0000"), "PENDING");

        mockMvc.perform(post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("Unauthorized"))
            .andExpect(jsonPath("$.correlationId").exists());

        createRoleWithoutPermissions("TEST_NO_RETURNS");
        insertUser("no_sales_returns", "correct-password", "TEST_NO_RETURNS");
        String noPermissionToken = login("no_sales_returns", "correct-password");

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId())
                    .header("Authorization", "Bearer " + noPermissionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Sin permisos",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(sale.salesOrderItemId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("Forbidden"))
            .andExpect(jsonPath("$.correlationId").exists());

        insertUser("sales_return_validation_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("sales_return_validation_admin", "correct-password");

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "return-flow-create-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Cantidad incorrecta",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 2
                            }
                          ]
                        }
                        """.formatted(sale.salesOrderItemId()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SALES_OPERATION"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void shouldCancelDraftReturnAndExposeOpenApi() throws Exception {
        insertUser("sales_return_cancel_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("sales_return_cancel_admin", "correct-password");
        TestContext context = createContext("RETURN-CANCEL");
        UUID presentationId = insertProductPresentation("RETURN-CANCEL");
        SaleFixture sale = insertSale(context, presentationId, null, new BigDecimal("1.000"), new BigDecimal("15.0000"), "PENDING");

        MvcResult created = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", sale.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "return-flow-create-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Borrador a cancelar",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 1
                            }
                          ]
                        }
                        """.formatted(sale.salesOrderItemId()))
            )
            .andExpect(status().isCreated())
            .andReturn();

        String returnId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.returnId");

        mockMvc.perform(
                post("/api/v1/sales/returns/{returnId}/cancel", returnId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SALES_RETURN_CANCELLED"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(
                get("/v3/api-docs")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/sales/orders/{salesOrderId}/returns']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/returns/{returnId}']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/returns/{returnId}/confirm']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/returns/{returnId}/cancel']").exists());
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
            "Sales Return Test " + username
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
                VALUES (?, ?, 'Rol de prueba sin permisos de devoluciones', FALSE)
                """,
            roleCode,
            roleCode
        );
    }

    private TestContext createContext(String prefix) {
        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cashRegisterId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbcTemplate.update(
            """
                INSERT INTO organization.branches (branch_id, code, name, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
            branchId,
            code(prefix, "B", suffix),
            prefix + " Branch"
        );
        jdbcTemplate.update(
            """
                INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            warehouseId,
            branchId,
            code(prefix, "W", suffix),
            prefix + " Warehouse"
        );
        jdbcTemplate.update(
            """
                INSERT INTO organization.cash_registers (cash_register_id, branch_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            cashRegisterId,
            branchId,
            code(prefix, "C", suffix),
            prefix + " Register"
        );
        return new TestContext(branchId, warehouseId, cashRegisterId);
    }

    private UUID insertProductPresentation(String prefix) {
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update("INSERT INTO catalog.categories (category_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", categoryId, code(prefix, "CAT", suffix), prefix + " Categoria");
        jdbcTemplate.update("INSERT INTO catalog.brands (brand_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", brandId, code(prefix, "BR", suffix), prefix + " Marca");
        jdbcTemplate.update("INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol, quantity_scale) VALUES (?, ?, ?, ?, 0)", unitId, code(prefix, "UN", suffix), prefix + " Unidad", "pz");
        jdbcTemplate.update("INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, ?, 0.160000, 'ACTIVE')", taxId, code(prefix, "TX", suffix), "IVA 16 " + suffix);
        jdbcTemplate.update(
            """
                INSERT INTO catalog.products (product_id, category_id, brand_id, name, description, product_type, tracks_inventory, tracks_lots, tracks_expiration, status)
                VALUES (?, ?, ?, ?, ?, 'GOODS', TRUE, TRUE, TRUE, 'ACTIVE')
                """,
            productId,
            categoryId,
            brandId,
            prefix + " Producto",
            "Producto de prueba para devoluciones"
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
        return presentationId;
    }

    private UUID insertLot(UUID presentationId, String lotNumber) {
        UUID lotId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO inventory.lots (lot_id, product_presentation_id, lot_number, manufactured_at, expires_at, status)
                VALUES (?, ?, ?, CURRENT_DATE - INTERVAL '1 day', CURRENT_DATE + INTERVAL '1 year', 'ACTIVE')
                """,
            lotId,
            presentationId,
            lotNumber + "-" + lotId.toString().substring(0, 8).toUpperCase()
        );
        return lotId;
    }

    private void insertStock(UUID warehouseId, UUID presentationId, UUID lotId, BigDecimal quantity) {
        jdbcTemplate.update(
            """
                INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost)
                VALUES (?, ?, ?, 12.5000)
                """,
            warehouseId,
            presentationId,
            quantity
        );
        if (lotId != null) {
            jdbcTemplate.update(
                """
                    INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity)
                    VALUES (?, ?, ?)
                    """,
                warehouseId,
                lotId,
                quantity
            );
        }
    }

    private SaleFixture insertSale(TestContext context, UUID presentationId, UUID lotId, BigDecimal quantity, BigDecimal total, String paymentStatus) {
        UUID salesOrderId = UUID.randomUUID();
        UUID salesOrderItemId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, channel, status,
                    payment_status, currency_code, subtotal, discount_total, tax_total, total,
                    idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, 'POS', 'CONFIRMED', ?, 'MXN', ?, 0, 0, ?, ?, clock_timestamp())
                """,
            salesOrderId,
            "SO-RET-" + salesOrderId.toString().substring(0, 8).toUpperCase(),
            context.branchId(),
            context.warehouseId(),
            paymentStatus,
            total,
            total,
            UUID.randomUUID()
        );
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_order_items (
                    sales_order_item_id, sales_order_id, product_presentation_id, lot_id,
                    product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost,
                    discount_amount, tax_rate, tax_amount, line_total
                ) VALUES (?, ?, ?, ?, 'Coca-Cola Original - Botella 600 ml', 'COCA-COLA-600ML', ?, 18.5000, 12.5000, 0, 0.160000, 5.9200, ?)
                """,
            salesOrderItemId,
            salesOrderId,
            presentationId,
            lotId,
            quantity,
            total
        );
        return new SaleFixture(salesOrderId, salesOrderItemId);
    }

    private UUID insertCapturedCashPayment(UUID salesOrderId, BigDecimal amount) {
        return insertCapturedPayment(salesOrderId, "CASH", amount);
    }

    private UUID insertCapturedPayment(UUID salesOrderId, String paymentMethod, BigDecimal amount) {
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.payments (payment_id, sales_order_id, payment_method, status, amount, idempotency_key, paid_at)
                VALUES (?, ?, ?, 'CAPTURED', ?, ?, clock_timestamp())
                """,
            paymentId,
            salesOrderId,
            paymentMethod,
            amount,
            UUID.randomUUID()
        );
        return paymentId;
    }

    private UUID insertOpenCashSession(UUID cashRegisterId, UUID userId, BigDecimal openingAmount) {
        UUID cashSessionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO cash.cash_sessions (cash_session_id, cash_register_id, opened_by, status, opening_amount, opened_at)
                VALUES (?, ?, ?, 'OPEN', ?, clock_timestamp())
                """,
            cashSessionId,
            cashRegisterId,
            userId,
            openingAmount
        );
        return cashSessionId;
    }

    private BigDecimal stockOnHand(UUID warehouseId, UUID presentationId) {
        return jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            warehouseId,
            presentationId
        );
    }

    private BigDecimal lotOnHand(UUID warehouseId, UUID lotId) {
        return jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.lot_balances WHERE warehouse_id = ? AND lot_id = ?",
            BigDecimal.class,
            warehouseId,
            lotId
        );
    }

    private String code(String prefix, String type, String suffix) {
        String compactPrefix = prefix.replace("-", "");
        if (compactPrefix.length() > 8) {
            compactPrefix = compactPrefix.substring(0, 8);
        }
        return compactPrefix + type + suffix;
    }

    private record TestContext(UUID branchId, UUID warehouseId, UUID cashRegisterId) {
    }

    private record SaleFixture(UUID salesOrderId, UUID salesOrderItemId) {
    }
}

