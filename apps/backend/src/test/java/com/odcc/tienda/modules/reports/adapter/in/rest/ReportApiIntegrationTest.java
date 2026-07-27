package com.odcc.tienda.modules.reports.adapter.in.rest;

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
class ReportApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldReturnOperationalReportsFromConfirmedDataOnly() throws Exception {
        insertUser("report_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_admin", "correct-password");
        TestData data = createOperationalData();

        mockMvc.perform(
                get("/api/v1/reports/sales-summary")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .param("customerId", data.customerId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("REPORT_SALES_SUMMARY_FOUND"))
            .andExpect(jsonPath("$.data.ticketCount").value(1))
            .andExpect(jsonPath("$.data.total").value(20.00))
            .andExpect(jsonPath("$.data.averageTicket").value(20.00));

        mockMvc.perform(
                get("/api/v1/reports/top-products")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .param("limit", "5")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].productPresentationId").value(data.presentationId().toString()))
            .andExpect(jsonPath("$.data[0].quantitySold").value(2.0))
            .andExpect(jsonPath("$.data[0].grossAmount").value(20.00));

        mockMvc.perform(
                get("/api/v1/reports/customer-sales")
                    .param("branchId", data.branchId().toString())
                    .param("customerId", data.customerId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].customerId").value(data.customerId().toString()))
            .andExpect(jsonPath("$.data[0].ticketCount").value(1))
            .andExpect(jsonPath("$.data[0].total").value(20.00));

        mockMvc.perform(
                get("/api/v1/reports/low-stock")
                    .param("warehouseId", data.warehouseId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].productPresentationId").value(data.presentationId().toString()))
            .andExpect(jsonPath("$.data[0].availableQuantity").value(2.0))
            .andExpect(jsonPath("$.data[0].minimumStock").value(5.0));

        mockMvc.perform(
                get("/api/v1/reports/inventory-movements")
                    .param("warehouseId", data.warehouseId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].movementType").value("SALE"))
            .andExpect(jsonPath("$.data[0].movementCount").value(1))
            .andExpect(jsonPath("$.data[0].totalQuantity").value(2.0));

        mockMvc.perform(
                get("/api/v1/reports/cash-summary")
                    .param("branchId", data.branchId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].cashSessionId").value(data.cashSessionId().toString()))
            .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
            .andExpect(jsonPath("$.data[0].cashIn").value(120.00))
            .andExpect(jsonPath("$.data[0].differenceAmount").value(0.00));
    }

    @Test
    void shouldProtectReportEndpointsAndExposeOpenApi() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales-summary"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        createRoleWithoutPermissions("TEST_NO_REPORTS");
        insertUser("no_reports", "correct-password", "TEST_NO_REPORTS");
        String forbiddenToken = login("no_reports", "correct-password");

        mockMvc.perform(get("/api/v1/reports/sales-summary").header("Authorization", "Bearer " + forbiddenToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        insertUser("report_openapi", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_openapi", "correct-password");

        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/reports/sales-summary']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/top-products']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/customer-sales']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/low-stock']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/inventory-movements']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/cash-summary']").exists());
    }

    @Test
    void shouldClampReportLimitToOneHundred() throws Exception {
        insertUser("report_limit", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_limit", "correct-password");

        MvcResult result = mockMvc.perform(
                get("/api/v1/reports/top-products")
                    .param("limit", "500")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andReturn();

        net.minidev.json.JSONArray data = JsonPath.read(result.getResponse().getContentAsString(), "$.data");
        assertEquals(true, data.size() <= 100);
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
        jdbcTemplate.update("""
                INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            userId,
            username,
            passwordEncoder.encode(password),
            "Report Test " + username
        );
        jdbcTemplate.update("INSERT INTO iam.user_roles (user_id, role_id) SELECT ?, role_id FROM iam.roles WHERE code = ?", userId, roleCode);
        return userId;
    }

    private void createRoleWithoutPermissions(String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO iam.roles (code, name, description, is_system)
                VALUES (?, ?, 'Rol de prueba sin permisos de reportes', FALSE)
                """,
            roleCode,
            roleCode
        );
    }

    private TestData createOperationalData() {
        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID confirmedOrderId = UUID.randomUUID();
        UUID cancelledOrderId = UUID.randomUUID();
        UUID movementId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID cashRegisterId = UUID.randomUUID();
        UUID cashSessionId = UUID.randomUUID();
        String suffix = branchId.toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update("INSERT INTO iam.users (user_id, username, password_hash, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')", userId, "report_actor_" + suffix.toLowerCase(), passwordEncoder.encode("correct-password"), "Report Actor");
        jdbcTemplate.update("INSERT INTO organization.branches (branch_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')", branchId, "RB" + suffix, "Report Branch");
        jdbcTemplate.update("INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')", warehouseId, branchId, "RW" + suffix, "Report Warehouse");
        jdbcTemplate.update("INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol) VALUES (?, ?, 'Pieza reporte', 'pz')", unitId, "RU" + suffix.substring(0, 6));
        jdbcTemplate.update("INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, 'IVA reporte', 0.160000, 'ACTIVE')", taxId, "RT" + suffix);
        jdbcTemplate.update("INSERT INTO catalog.products (product_id, name, tracks_inventory, status) VALUES (?, ?, TRUE, 'ACTIVE')", productId, "Producto Reporte " + suffix);
        jdbcTemplate.update("""
                INSERT INTO catalog.product_presentations (product_presentation_id, product_id, unit_id, tax_id, sku, name, minimum_stock, status)
                VALUES (?, ?, ?, ?, ?, ?, 5, 'ACTIVE')
                """, presentationId, productId, unitId, taxId, "RSKU" + suffix, "Presentacion Reporte");
        jdbcTemplate.update("INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, reserved_quantity, allocated_quantity, average_unit_cost) VALUES (?, ?, 2, 0, 0, 5)", warehouseId, presentationId);
        jdbcTemplate.update("INSERT INTO sales.customers (customer_id, customer_code, customer_type, display_name, status) VALUES (?, ?, 'PERSON', ?, 'ACTIVE')", customerId, "RC" + suffix, "Cliente Reporte");
        jdbcTemplate.update("""
                INSERT INTO sales.sales_orders (sales_order_id, order_number, branch_id, warehouse_id, customer_id, channel, status, payment_status, currency_code, subtotal, discount_total, tax_total, total, idempotency_key, confirmed_at)
                VALUES (?, ?, ?, ?, ?, 'POS', 'CONFIRMED', 'PAID', 'MXN', 17.24, 0, 2.76, 20.00, ?, clock_timestamp())
                """, confirmedOrderId, "RSO-" + suffix, branchId, warehouseId, customerId, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO sales.sales_orders (sales_order_id, order_number, branch_id, warehouse_id, customer_id, channel, status, payment_status, currency_code, subtotal, discount_total, tax_total, total, idempotency_key, confirmed_at)
                VALUES (?, ?, ?, ?, ?, 'POS', 'CANCELLED', 'CANCELLED', 'MXN', 999, 0, 0, 999, ?, clock_timestamp())
                """, cancelledOrderId, "RSO-C-" + suffix, branchId, warehouseId, customerId, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO sales.sales_order_items (sales_order_item_id, sales_order_id, product_presentation_id, product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost, discount_amount, tax_rate, tax_amount, line_total)
                VALUES (?, ?, ?, 'Producto Reporte', ?, 2, 10.00, 5.00, 0, 0, 0, 20.00)
                """, UUID.randomUUID(), confirmedOrderId, presentationId, "RSKU" + suffix);
        jdbcTemplate.update("""
                INSERT INTO sales.sales_order_items (sales_order_item_id, sales_order_id, product_presentation_id, product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost, discount_amount, tax_rate, tax_amount, line_total)
                VALUES (?, ?, ?, 'Producto Cancelado', ?, 99, 10.00, 5.00, 0, 0, 0, 999.00)
                """, UUID.randomUUID(), cancelledOrderId, presentationId, "RSKU" + suffix);
        jdbcTemplate.update("INSERT INTO inventory.stock_movements (stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, created_by, confirmed_at) VALUES (?, ?, ?, 'SALE', 'CONFIRMED', 'SALES_ORDER', ?, ?, clock_timestamp())", movementId, branchId, warehouseId, confirmedOrderId, userId);
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_movement_items (stock_movement_item_id, stock_movement_id, product_presentation_id, direction, quantity, unit_cost, quantity_before, quantity_after)
                VALUES (?, ?, ?, 'OUT', 2, 5, 4, 2)
                """, UUID.randomUUID(), movementId, presentationId);
        jdbcTemplate.update("INSERT INTO organization.cash_registers (cash_register_id, branch_id, code, name, status) VALUES (?, ?, ?, 'Caja Reporte', 'ACTIVE')", cashRegisterId, branchId, "RCR" + suffix.substring(0, 6));
        jdbcTemplate.update("""
                INSERT INTO cash.cash_sessions (cash_session_id, cash_register_id, opened_by, closed_by, status, opening_amount, expected_amount, counted_amount, difference_amount, closed_at)
                VALUES (?, ?, ?, ?, 'CLOSED', 100, 120, 120, 0, clock_timestamp())
                """, cashSessionId, cashRegisterId, userId, userId);
        jdbcTemplate.update("INSERT INTO cash.cash_movements (cash_movement_id, cash_session_id, movement_type, direction, amount, reference, reason, created_by) VALUES (?, ?, 'OPENING', 'IN', 100, 'Apertura', 'Apertura reporte', ?)", UUID.randomUUID(), cashSessionId, userId);
        jdbcTemplate.update("INSERT INTO cash.cash_movements (cash_movement_id, cash_session_id, movement_type, direction, amount, reference, reason, created_by) VALUES (?, ?, 'SALE', 'IN', 20, 'Venta', 'Venta reporte', ?)", UUID.randomUUID(), cashSessionId, userId);
        return new TestData(branchId, warehouseId, presentationId, customerId, cashSessionId);
    }

    private record TestData(UUID branchId, UUID warehouseId, UUID presentationId, UUID customerId, UUID cashSessionId) {
    }
}
