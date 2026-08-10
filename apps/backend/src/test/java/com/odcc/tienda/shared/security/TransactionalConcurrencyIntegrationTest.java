package com.odcc.tienda.shared.security;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TransactionalConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldPreventConcurrentOverpaymentAndDuplicateCashClose() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");

        MvcResult openResult = mockMvc.perform(
                post("/api/v1/cash/sessions/open")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashRegisterId": "%s",
                          "openingAmount": 100.00,
                          "notes": "Prueba concurrente"
                        }
                        """.formatted(fixture.cashRegisterId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        UUID cashSessionId = UUID.fromString(JsonPath.read(
            openResult.getResponse().getContentAsString(), "$.data.cashSessionId"
        ));

        List<Integer> paymentStatuses = runConcurrently(
            paymentRequest(token, fixture.salesOrderId(), cashSessionId, UUID.randomUUID()),
            paymentRequest(token, fixture.salesOrderId(), cashSessionId, UUID.randomUUID())
        );
        assertEquals(List.of(201, 409), paymentStatuses);
        Integer capturedPayments = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales.payments WHERE sales_order_id = ? AND status = 'CAPTURED'",
            Integer.class,
            fixture.salesOrderId()
        );
        BigDecimal capturedAmount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM sales.payments WHERE sales_order_id = ? AND status = 'CAPTURED'",
            BigDecimal.class,
            fixture.salesOrderId()
        );
        assertEquals(1, capturedPayments);
        assertEquals(0, new BigDecimal("60.0000").compareTo(capturedAmount));

        List<Integer> closeStatuses = runConcurrently(
            closeRequest(token, cashSessionId),
            closeRequest(token, cashSessionId)
        );
        assertEquals(List.of(200, 409), closeStatuses);
        Integer closingMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cash.cash_movements WHERE cash_session_id = ? AND movement_type = 'CLOSING'",
            Integer.class,
            cashSessionId
        );
        assertEquals(1, closingMovements);
    }

    @Test
    void shouldConfirmAConcurrentSalesReturnOnlyOnce() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture returnFixture = createReturnFixture(fixture);

        MvcResult createResult = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", returnFixture.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Confirmacion concurrente",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 2
                            }
                          ]
                        }
                        """.formatted(returnFixture.salesOrderItemId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        UUID returnId = UUID.fromString(JsonPath.read(
            createResult.getResponse().getContentAsString(), "$.data.returnId"
        ));

        List<Integer> confirmStatuses = runConcurrently(
            confirmReturnRequest(token, returnId),
            confirmReturnRequest(token, returnId)
        );
        assertEquals(List.of(200, 409), confirmStatuses);

        Integer returnMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE source_id = ? AND movement_type = 'SALE_RETURN'",
            Integer.class,
            returnId
        );
        BigDecimal finalStock = jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            fixture.warehouseId(),
            returnFixture.presentationId()
        );
        assertEquals(1, returnMovements);
        assertEquals(0, new BigDecimal("5.000").compareTo(finalStock));
    }

    @Test
    void shouldApplyAConcurrentInventoryReceiptIdempotencyKeyOnlyOnce() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture productFixture = createReturnFixture(fixture);
        UUID idempotencyKey = UUID.randomUUID();

        List<Integer> receiptStatuses = runConcurrently(
            receiptRequest(token, fixture.warehouseId(), productFixture.presentationId(), idempotencyKey),
            receiptRequest(token, fixture.warehouseId(), productFixture.presentationId(), idempotencyKey)
        );
        assertEquals(List.of(201, 201), receiptStatuses);

        BigDecimal finalStock = jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            fixture.warehouseId(),
            productFixture.presentationId()
        );
        Integer receiptMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE idempotency_key = ? AND movement_type = 'PURCHASE_RECEIPT'",
            Integer.class,
            idempotencyKey
        );
        Integer fingerprintLength = jdbcTemplate.queryForObject(
            "SELECT LENGTH(source_fingerprint) FROM inventory.stock_movements WHERE idempotency_key = ?",
            Integer.class,
            idempotencyKey
        );
        assertEquals(0, new BigDecimal("5.000").compareTo(finalStock));
        assertEquals(1, receiptMovements);
        assertEquals(64, fingerprintLength);
    }

    private Callable<MvcResult> paymentRequest(
        String token,
        UUID salesOrderId,
        UUID cashSessionId,
        UUID idempotencyKey
    ) {
        return () -> mockMvc.perform(
            post("/api/v1/sales/orders/{salesOrderId}/payments", salesOrderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cashSessionId": "%s",
                      "paymentMethod": "CARD",
                      "amount": 60.00,
                      "currencyCode": "MXN",
                      "reference": "CONCURRENT",
                      "idempotencyKey": "%s"
                    }
                    """.formatted(cashSessionId, idempotencyKey))
        ).andReturn();
    }

    private Callable<MvcResult> closeRequest(String token, UUID cashSessionId) {
        return () -> mockMvc.perform(
            post("/api/v1/cash/sessions/{cashSessionId}/close", cashSessionId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "countedCashAmount": 100.00,
                      "notes": "Cierre concurrente"
                    }
                    """)
        ).andReturn();
    }

    private Callable<MvcResult> confirmReturnRequest(String token, UUID returnId) {
        return () -> mockMvc.perform(
            post("/api/v1/sales/returns/{returnId}/confirm", returnId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashSessionId\":null}")
        ).andReturn();
    }

    private Callable<MvcResult> receiptRequest(
        String token,
        UUID warehouseId,
        UUID presentationId,
        UUID idempotencyKey
    ) {
        return () -> mockMvc.perform(
            post("/api/v1/inventory/receipts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "warehouseId": "%s",
                      "supplierId": null,
                      "idempotencyKey": "%s",
                      "reason": "Recepcion concurrente",
                      "items": [
                        {
                          "productPresentationId": "%s",
                          "lotNumber": null,
                          "manufacturedAt": null,
                          "expiresAt": null,
                          "quantity": 2,
                          "unitCost": 10.00
                        }
                      ],
                      "pallets": []
                    }
                    """.formatted(warehouseId, idempotencyKey, presentationId))
        ).andReturn();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(Callable<MvcResult>... requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.length);
        CountDownLatch ready = new CountDownLatch(requests.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return request.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            }
            Collections.sort(statuses);
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                post("/api/v1/auth/login")
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

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID userId = UUID.randomUUID();
        String username = "concurrency_" + suffix.toLowerCase();
        jdbcTemplate.update(
            "INSERT INTO iam.users (user_id, username, password_hash, display_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            userId, username, passwordEncoder.encode("correct-password"), "Concurrency Test"
        );
        jdbcTemplate.update(
            "INSERT INTO iam.user_roles (user_id, role_id) SELECT ?, role_id FROM iam.roles WHERE code = 'SYSTEM_ADMIN'",
            userId
        );

        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cashRegisterId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO organization.branches (branch_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
            branchId, "CON-B-" + suffix, "Concurrency Branch"
        );
        jdbcTemplate.update(
            "INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            warehouseId, branchId, "CON-W-" + suffix, "Concurrency Warehouse"
        );
        jdbcTemplate.update(
            "INSERT INTO organization.cash_registers (cash_register_id, branch_id, code, name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
            cashRegisterId, branchId, "CON-C-" + suffix, "Concurrency Register"
        );

        UUID salesOrderId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, channel, status,
                    payment_status, currency_code, subtotal, discount_total, tax_total, total,
                    idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, 'POS', 'CONFIRMED', 'PENDING', 'MXN', 100, 0, 0, 100, ?, clock_timestamp())
                """,
            salesOrderId, "SO-CON-" + suffix, branchId, warehouseId, UUID.randomUUID()
        );
        return new Fixture(username, branchId, warehouseId, cashRegisterId, salesOrderId);
    }

    private ReturnFixture createReturnFixture(Fixture fixture) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID categoryId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID taxId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO catalog.categories (category_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
            categoryId, "CC" + suffix, "Concurrency Category"
        );
        jdbcTemplate.update(
            "INSERT INTO catalog.brands (brand_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE')",
            brandId, "CB" + suffix, "Concurrency Brand"
        );
        jdbcTemplate.update(
            "INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol, quantity_scale) VALUES (?, ?, ?, 'pz', 0)",
            unitId, "CU" + suffix, "Concurrency Unit"
        );
        jdbcTemplate.update(
            "INSERT INTO catalog.taxes (tax_id, code, name, rate, status) VALUES (?, ?, ?, 0, 'ACTIVE')",
            taxId, "CT" + suffix, "Concurrency Tax"
        );
        jdbcTemplate.update(
            """
                INSERT INTO catalog.products (
                    product_id, category_id, brand_id, name, product_type,
                    tracks_inventory, tracks_lots, tracks_expiration, status
                ) VALUES (?, ?, ?, ?, 'GOODS', TRUE, FALSE, FALSE, 'ACTIVE')
                """,
            productId, categoryId, brandId, "Concurrency Product"
        );
        jdbcTemplate.update(
            """
                INSERT INTO catalog.product_presentations (
                    product_presentation_id, product_id, unit_id, tax_id, sku, name,
                    conversion_factor, net_content, minimum_stock, status
                ) VALUES (?, ?, ?, ?, ?, ?, 1, 1, 1, 'ACTIVE')
                """,
            presentationId, productId, unitId, taxId, "CSKU" + suffix, "Concurrency Presentation"
        );
        jdbcTemplate.update(
            "INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost) VALUES (?, ?, 3, 10)",
            fixture.warehouseId(), presentationId
        );

        UUID salesOrderId = UUID.randomUUID();
        UUID salesOrderItemId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, channel, status,
                    payment_status, currency_code, subtotal, discount_total, tax_total, total,
                    idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, 'POS', 'CONFIRMED', 'PENDING', 'MXN', 40, 0, 0, 40, ?, clock_timestamp())
                """,
            salesOrderId, "SO-RET-CON-" + suffix, fixture.branchId(), fixture.warehouseId(), UUID.randomUUID()
        );
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_order_items (
                    sales_order_item_id, sales_order_id, product_presentation_id,
                    product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost,
                    discount_amount, tax_rate, tax_amount, line_total
                ) VALUES (?, ?, ?, 'Concurrency Product', ?, 2, 20, 10, 0, 0, 0, 40)
                """,
            salesOrderItemId, salesOrderId, presentationId, "CSKU" + suffix
        );
        return new ReturnFixture(salesOrderId, salesOrderItemId, presentationId);
    }

    private record Fixture(
        String username,
        UUID branchId,
        UUID warehouseId,
        UUID cashRegisterId,
        UUID salesOrderId
    ) {
    }

    private record ReturnFixture(UUID salesOrderId, UUID salesOrderItemId, UUID presentationId) {
    }
}
