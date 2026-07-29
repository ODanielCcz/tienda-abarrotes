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

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportV2ApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCalculateSalesReturnsMarginValuationAndExpiration() throws Exception {
        insertUser("report_v2_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_v2_admin", "correct-password");
        TestData data = createReportData();

        mockMvc.perform(
                get("/api/v1/reports/sales-by-period")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .param("groupBy", "DAY")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "report-v2-sales")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("REPORT_SALES_BY_PERIOD_FOUND"))
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.correlationId").value("report-v2-sales"))
            .andExpect(jsonPath("$.data[0].ticketCount").value(3))
            .andExpect(jsonPath("$.data[0].grossSubtotal").value(130.00))
            .andExpect(jsonPath("$.data[0].taxTotal").value(20.80))
            .andExpect(jsonPath("$.data[0].grossSales").value(150.80))
            .andExpect(jsonPath("$.data[0].returnsAmount").value(34.80))
            .andExpect(jsonPath("$.data[0].netSales").value(116.00));

        mockMvc.perform(
                get("/api/v1/reports/gross-margin")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.grossRevenueExcludingTax").value(130.00))
            .andExpect(jsonPath("$.data.returnedRevenueExcludingTax").value(30.00))
            .andExpect(jsonPath("$.data.netRevenueExcludingTax").value(100.00))
            .andExpect(jsonPath("$.data.grossCost").value(52.00))
            .andExpect(jsonPath("$.data.returnedCost").value(12.00))
            .andExpect(jsonPath("$.data.netCost").value(40.00))
            .andExpect(jsonPath("$.data.grossProfit").value(60.00))
            .andExpect(jsonPath("$.data.grossMarginPercent").value(60.0000));

        mockMvc.perform(
                get("/api/v1/reports/product-profitability")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].productPresentationId").value(data.presentationId().toString()))
            .andExpect(jsonPath("$.data[0].quantitySold").value(13.0))
            .andExpect(jsonPath("$.data[0].quantityReturned").value(3.0))
            .andExpect(jsonPath("$.data[0].netQuantity").value(10.0))
            .andExpect(jsonPath("$.data[0].netRevenueExcludingTax").value(100.00))
            .andExpect(jsonPath("$.data[0].netCost").value(40.00))
            .andExpect(jsonPath("$.data[0].grossProfit").value(60.00));

        mockMvc.perform(
                get("/api/v1/reports/stock-valuation")
                    .param("warehouseId", data.warehouseId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalOnHandQuantity").value(12.0))
            .andExpect(jsonPath("$.data.totalStockValue").value(48.00))
            .andExpect(jsonPath("$.data.items[0].availableQuantity").value(10.0))
            .andExpect(jsonPath("$.data.items[0].averageUnitCost").value(4.00));

        mockMvc.perform(
                get("/api/v1/reports/expiring-products")
                    .param("warehouseId", data.warehouseId().toString())
                    .param("days", "30")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].lotId").value(data.expiringLotId().toString()))
            .andExpect(jsonPath("$.data[0].onHandQuantity").value(5.0))
            .andExpect(jsonPath("$.data[0].estimatedValue").value(20.00));

        mockMvc.perform(
                get("/api/v1/reports/returns-summary")
                    .param("branchId", data.branchId().toString())
                    .param("warehouseId", data.warehouseId().toString())
                    .param("groupBy", "MONTH")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.returnCount").value(2))
            .andExpect(jsonPath("$.data.returnedQuantity").value(3.0))
            .andExpect(jsonPath("$.data.returnedAmount").value(34.80))
            .andExpect(jsonPath("$.data.periods[0].returnCount").value(2));
    }

    @Test
    void shouldSupportAllPeriodGroupsAndDefaultDateRange() throws Exception {
        insertUser("report_v2_groups", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_v2_groups", "correct-password");
        TestData data = createReportData();

        for (String groupBy : new String[]{"DAY", "WEEK", "MONTH"}) {
            mockMvc.perform(
                    get("/api/v1/reports/sales-by-period")
                        .param("branchId", data.branchId().toString())
                        .param("groupBy", groupBy)
                        .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].grossSales").value(150.80));
        }

        mockMvc.perform(
                get("/api/v1/reports/returns-summary")
                    .param("branchId", data.branchId().toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.returnCount").value(2));
    }

    @Test
    void shouldRejectInvalidFilters() throws Exception {
        insertUser("report_v2_filters", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_v2_filters", "correct-password");
        LocalDate today = LocalDate.now();

        mockMvc.perform(
                get("/api/v1/reports/sales-by-period")
                    .param("from", today.minusDays(400).toString())
                    .param("to", today.toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REPORT_FILTER"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(
                get("/api/v1/reports/sales-by-period")
                    .param("groupBy", "YEAR")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REPORT_FILTER"));

        mockMvc.perform(
                get("/api/v1/reports/expiring-products")
                    .param("days", "366")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REPORT_FILTER"));
    }

    @Test
    void shouldProtectAndExposeReportV2Endpoints() throws Exception {
        mockMvc.perform(get("/api/v1/reports/sales-by-period"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("Unauthorized"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        createRoleWithoutPermissions("TEST_NO_REPORTS_V2");
        insertUser("no_reports_v2", "correct-password", "TEST_NO_REPORTS_V2");
        String forbiddenToken = login("no_reports_v2", "correct-password");

        mockMvc.perform(
                get("/api/v1/reports/gross-margin")
                    .header("Authorization", "Bearer " + forbiddenToken)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("Forbidden"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        insertUser("report_v2_openapi", "correct-password", "SYSTEM_ADMIN");
        String token = login("report_v2_openapi", "correct-password");

        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/reports/sales-by-period']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/gross-margin']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/product-profitability']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/stock-valuation']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/expiring-products']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/reports/returns-summary']").exists());
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
            "Report v2 " + username
        );
        jdbcTemplate.update(
            "INSERT INTO iam.user_roles (user_id, role_id) SELECT ?, role_id FROM iam.roles WHERE code = ?",
            userId,
            roleCode
        );
        return userId;
    }

    private void createRoleWithoutPermissions(String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO iam.roles (code, name, description, is_system)
                VALUES (?, ?, 'Rol de prueba sin permisos v2', FALSE)
                """,
            roleCode,
            roleCode
        );
    }

    private TestData createReportData() {
        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID expiringLotId = UUID.randomUUID();
        UUID futureLotId = UUID.randomUUID();
        String suffix = branchId.toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update(
            "INSERT INTO organization.branches (branch_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
            branchId, "V2B" + suffix, "Report v2 Branch"
        );
        jdbcTemplate.update(
            "INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            warehouseId, branchId, "V2W" + suffix, "Report v2 Warehouse"
        );
        jdbcTemplate.update(
            "INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol) VALUES (?, ?, 'Pieza reporte v2', 'pz')",
            unitId, "V2U" + suffix.substring(0, 6)
        );
        jdbcTemplate.update(
            "INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, 'IVA reporte v2', 0.160000, 'ACTIVE')",
            taxId, "V2T" + suffix
        );
        jdbcTemplate.update("""
                INSERT INTO catalog.products (
                    product_id, name, tracks_inventory, tracks_lots, tracks_expiration, status
                ) VALUES (?, ?, TRUE, TRUE, TRUE, 'ACTIVE')
                """,
            productId, "Producto Reporte v2 " + suffix
        );
        jdbcTemplate.update("""
                INSERT INTO catalog.product_presentations (
                    product_presentation_id, product_id, unit_id, tax_id, sku, name, minimum_stock, status
                ) VALUES (?, ?, ?, ?, ?, ?, 5, 'ACTIVE')
                """,
            presentationId, productId, unitId, taxId, "V2SKU" + suffix, "Presentacion Reporte v2"
        );
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_balances (
                    warehouse_id, product_presentation_id, on_hand_quantity,
                    reserved_quantity, allocated_quantity, average_unit_cost
                ) VALUES (?, ?, 12, 2, 0, 4)
                """,
            warehouseId, presentationId
        );
        jdbcTemplate.update("""
                INSERT INTO inventory.lots (
                    lot_id, product_presentation_id, lot_number, manufactured_at, expires_at, status
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """,
            expiringLotId,
            presentationId,
            "V2-EXP-" + suffix,
            LocalDate.now().minusDays(10),
            LocalDate.now().plusDays(10)
        );
        jdbcTemplate.update("""
                INSERT INTO inventory.lots (
                    lot_id, product_presentation_id, lot_number, manufactured_at, expires_at, status
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
                """,
            futureLotId,
            presentationId,
            "V2-FUT-" + suffix,
            LocalDate.now().minusDays(10),
            LocalDate.now().plusDays(90)
        );
        jdbcTemplate.update(
            "INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity) VALUES (?, ?, 5)",
            warehouseId, expiringLotId
        );
        jdbcTemplate.update(
            "INSERT INTO inventory.lot_balances (warehouse_id, lot_id, on_hand_quantity) VALUES (?, ?, 7)",
            warehouseId, futureLotId
        );
        jdbcTemplate.update("""
                INSERT INTO sales.customers (
                    customer_id, customer_code, customer_type, display_name, status
                ) VALUES (?, ?, 'PERSON', ?, 'ACTIVE')
                """,
            customerId, "V2C" + suffix, "Cliente Reporte v2"
        );

        OrderData partial = insertOrder(
            branchId, warehouseId, customerId, presentationId, suffix + "P",
            "PARTIALLY_RETURNED", 10, 100, 16, 116
        );
        OrderData full = insertOrder(
            branchId, warehouseId, customerId, presentationId, suffix + "F",
            "RETURNED", 1, 10, 1.60, 11.60
        );
        insertOrder(
            branchId, warehouseId, customerId, presentationId, suffix + "C",
            "CONFIRMED", 2, 20, 3.20, 23.20
        );
        insertOrder(
            branchId, warehouseId, customerId, presentationId, suffix + "X",
            "CANCELLED", 99, 990, 158.40, 1148.40
        );

        insertReturn(partial, "CONFIRMED", 2, 23.20, "Devolucion parcial confirmada");
        insertReturn(full, "CONFIRMED", 1, 11.60, "Devolucion total confirmada");
        insertReturn(partial, "DRAFT", 1, 11.60, "Devolucion borrador");
        insertReturn(partial, "CANCELLED", 1, 11.60, "Devolucion cancelada");

        return new TestData(branchId, warehouseId, presentationId, expiringLotId);
    }

    private OrderData insertOrder(
        UUID branchId,
        UUID warehouseId,
        UUID customerId,
        UUID presentationId,
        String suffix,
        String status,
        int quantity,
        double subtotal,
        double tax,
        double total
    ) {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, customer_id,
                    channel, status, payment_status, currency_code, subtotal,
                    discount_total, tax_total, total, idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, ?, 'POS', ?, 'PAID', 'MXN', ?, 0, ?, ?, ?, clock_timestamp())
                """,
            orderId,
            "V2SO-" + suffix,
            branchId,
            warehouseId,
            customerId,
            status,
            subtotal,
            tax,
            total,
            UUID.randomUUID()
        );
        jdbcTemplate.update("""
                INSERT INTO sales.sales_order_items (
                    sales_order_item_id, sales_order_id, product_presentation_id,
                    product_name_snapshot, sku_snapshot, quantity, unit_price,
                    unit_cost, discount_amount, tax_rate, tax_amount, line_total
                ) VALUES (?, ?, ?, 'Producto Reporte v2', ?, ?, 10, 4, 0, 0.16, ?, ?)
                """,
            itemId,
            orderId,
            presentationId,
            "V2SKU-" + suffix,
            quantity,
            tax,
            total
        );
        return new OrderData(orderId, itemId);
    }

    private void insertReturn(
        OrderData order,
        String status,
        int quantity,
        double amount,
        String reason
    ) {
        UUID returnId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO sales.returns (
                    return_id, sales_order_id, status, reason, total, confirmed_at
                ) VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'CONFIRMED' THEN clock_timestamp() ELSE NULL END)
                """,
            returnId,
            order.orderId(),
            status,
            reason,
            amount,
            status
        );
        jdbcTemplate.update("""
                INSERT INTO sales.return_items (
                    return_item_id, return_id, sales_order_item_id, quantity, amount
                ) VALUES (?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            returnId,
            order.itemId(),
            quantity,
            amount
        );
    }

    private record OrderData(UUID orderId, UUID itemId) {
    }

    private record TestData(
        UUID branchId,
        UUID warehouseId,
        UUID presentationId,
        UUID expiringLotId
    ) {
    }
}
