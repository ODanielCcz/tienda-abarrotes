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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    void shouldCancelAConcurrentSalesOrderOnlyOnce() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture orderFixture = createReturnFixture(fixture);

        List<Integer> cancelStatuses = runConcurrently(
            cancelSalesOrderRequest(token, orderFixture.salesOrderId()),
            cancelSalesOrderRequest(token, orderFixture.salesOrderId())
        );

        assertEquals(List.of(200, 409), cancelStatuses);
        Integer cancellationMovements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE source_id = ? AND movement_type = 'SALE_RETURN'",
            Integer.class,
            orderFixture.salesOrderId()
        );
        BigDecimal finalStock = jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            fixture.warehouseId(),
            orderFixture.presentationId()
        );
        assertEquals(1, cancellationMovements);
        assertEquals(0, new BigDecimal("5.000").compareTo(finalStock));
    }

    @Test
    void shouldRejectSalesOrderCancellationWhenANonCancelledReturnExists() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture orderFixture = createReturnFixture(fixture);
        UUID returnId = createReturnDraft(token, orderFixture);

        MvcResult result = cancelSalesOrderRequest(token, orderFixture.salesOrderId()).call();

        assertEquals(409, result.getResponse().getStatus());
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
            "SELECT status FROM sales.sales_orders WHERE sales_order_id = ?",
            String.class,
            orderFixture.salesOrderId()
        ));
        assertEquals("DRAFT", jdbcTemplate.queryForObject(
            "SELECT status FROM sales.returns WHERE return_id = ?",
            String.class,
            returnId
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE source_id = ? AND source_type = 'SALES_ORDER_CANCEL'",
            Integer.class,
            orderFixture.salesOrderId()
        ));
    }

    @Test
    void shouldRejectReturnConfirmationAfterSalesOrderCancellation() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture orderFixture = createReturnFixture(fixture);
        UUID returnId = createReturnDraft(token, orderFixture);
        jdbcTemplate.update("UPDATE sales.returns SET status = 'CANCELLED' WHERE return_id = ?", returnId);
        assertEquals(200, cancelSalesOrderRequest(token, orderFixture.salesOrderId()).call().getResponse().getStatus());
        jdbcTemplate.update("UPDATE sales.returns SET status = 'DRAFT' WHERE return_id = ?", returnId);

        MvcResult result = confirmReturnRequest(token, returnId).call();

        assertEquals(409, result.getResponse().getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory.stock_movements WHERE movement_type = 'SALE_RETURN' AND (source_id = ? OR source_id = ?)",
            Integer.class,
            orderFixture.salesOrderId(),
            returnId
        ));
        BigDecimal finalStock = jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            fixture.warehouseId(),
            orderFixture.presentationId()
        );
        assertEquals(0, new BigDecimal("5.000").compareTo(finalStock));
    }

    @Test
    void shouldRejectSalesOrderCancellationWhenCapturedPaymentExists() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        MvcResult payment = paymentRequest(token, fixture.salesOrderId(), UUID.randomUUID(), UUID.randomUUID()).call();
        assertEquals(201, payment.getResponse().getStatus());

        MvcResult result = cancelSalesOrderRequest(token, fixture.salesOrderId()).call();

        assertEquals(409, result.getResponse().getStatus());
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
            "SELECT status FROM sales.sales_orders WHERE sales_order_id = ?",
            String.class,
            fixture.salesOrderId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales.payments WHERE sales_order_id = ? AND status = 'CAPTURED'",
            Integer.class,
            fixture.salesOrderId()
        ));
    }

    @Test
    void shouldCreateOnePurchaseForConcurrentCanonicalIdempotentRequestsAndRejectChangedReplay() throws Exception {
        Fixture fixture = createFixture();
        String token = login(fixture.username(), "correct-password");
        ReturnFixture productFixture = createReturnFixture(fixture);
        UUID supplierId = createSupplier();
        UUID idempotencyKey = UUID.randomUUID();

        List<MvcResult> results = runConcurrentlyResults(
            purchaseRequest(token, fixture.warehouseId(), supplierId, productFixture.presentationId(), idempotencyKey, "10.00"),
            purchaseRequest(token, fixture.warehouseId(), supplierId, productFixture.presentationId(), idempotencyKey, "10.0000")
        );

        assertEquals(List.of(201, 201), results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM purchasing.purchases WHERE idempotency_key = ?",
            Integer.class,
            idempotencyKey
        ));
        List<String> purchaseIds = results.stream()
            .map(result -> (String) JsonPath.read(
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                "$.data.purchaseId"
            ))
            .distinct()
            .toList();
        assertEquals(1, purchaseIds.size());

        MvcResult changedReplay = purchaseRequest(
            token,
            fixture.warehouseId(),
            supplierId,
            productFixture.presentationId(),
            idempotencyKey,
            "11.00"
        ).call();
        assertEquals(409, changedReplay.getResponse().getStatus());
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

    @Test
    void shouldReturnTheSameSalesOrderForConcurrentIdempotentRequests() throws Exception {
        Fixture fixture = createFixture();
        ReturnFixture productFixture = createReturnFixture(fixture);
        String token = login(fixture.username(), "correct-password");
        UUID idempotencyKey = UUID.randomUUID();
        UUID priceListId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO catalog.price_lists (price_list_id, code, name, currency_code, status)
                VALUES (?, 'GENERAL', 'General', 'MXN', 'ACTIVE')
                ON CONFLICT (code) DO UPDATE SET status = 'ACTIVE'
                """,
            priceListId
        );
        UUID activePriceListId = jdbcTemplate.queryForObject(
            "SELECT price_list_id FROM catalog.price_lists WHERE code = 'GENERAL'",
            UUID.class
        );
        jdbcTemplate.update(
            """
                INSERT INTO catalog.prices (price_id, price_list_id, branch_id, product_presentation_id, amount)
                VALUES (?, ?, ?, ?, 20.0000)
                """,
            UUID.randomUUID(),
            activePriceListId,
            fixture.branchId(),
            productFixture.presentationId()
        );
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION sales.delay_sales_order_insert_for_test()
            RETURNS trigger
            LANGUAGE plpgsql
            AS $$
            BEGIN
                PERFORM pg_sleep(0.75);
                RETURN NEW;
            END;
            $$
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER delay_sales_order_insert_for_test
            AFTER INSERT ON sales.sales_orders
            FOR EACH ROW
            EXECUTE FUNCTION sales.delay_sales_order_insert_for_test()
            """);

        List<MvcResult> results = runConcurrentlyResults(
            salesOrderRequest(token, fixture.warehouseId(), productFixture.presentationId(), idempotencyKey),
            salesOrderRequest(token, fixture.warehouseId(), productFixture.presentationId(), idempotencyKey)
        );

        assertEquals(List.of(201, 201), results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
        String firstOrderId = JsonPath.read(results.get(0).getResponse().getContentAsString(), "$.data.salesOrderId");
        String secondOrderId = JsonPath.read(results.get(1).getResponse().getContentAsString(), "$.data.salesOrderId");
        assertEquals(firstOrderId, secondOrderId);
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sales.sales_orders WHERE idempotency_key = ?",
            Integer.class,
            idempotencyKey
        ));
        assertEquals(new BigDecimal("2.000"), jdbcTemplate.queryForObject(
            "SELECT on_hand_quantity FROM inventory.stock_balances WHERE warehouse_id = ? AND product_presentation_id = ?",
            BigDecimal.class,
            fixture.warehouseId(),
            productFixture.presentationId()
        ));
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

    private Callable<MvcResult> cancelSalesOrderRequest(String token, UUID salesOrderId) {
        return () -> mockMvc.perform(
            post("/api/v1/sales/orders/{salesOrderId}/cancel", salesOrderId)
                .header("Authorization", "Bearer " + token)
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

    private Callable<MvcResult> purchaseRequest(
        String token,
        UUID warehouseId,
        UUID supplierId,
        UUID presentationId,
        UUID idempotencyKey,
        String unitCost
    ) {
        return () -> mockMvc.perform(
            post("/api/v1/purchasing/purchases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "warehouseId": "%s",
                      "supplierId": "%s",
                      "supplierDocument": "CONCURRENT-PURCHASE",
                      "currencyCode": "MXN",
                      "idempotencyKey": "%s",
                      "items": [
                        {
                          "productPresentationId": "%s",
                          "quantity": 2,
                          "unitCost": %s,
                          "discountAmount": 0,
                          "taxAmount": 0
                        }
                      ]
                    }
                    """.formatted(warehouseId, supplierId, idempotencyKey, presentationId, unitCost))
        ).andReturn();
    }

    private Callable<MvcResult> salesOrderRequest(
        String token,
        UUID warehouseId,
        UUID presentationId,
        UUID idempotencyKey
    ) {
        return () -> mockMvc.perform(
                post("/api/v1/sales/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "channel": "POS",
                          "currencyCode": "MXN",
                          "idempotencyKey": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "quantity": 1,
                              "unitPrice": 20.00,
                              "discountAmount": 0
                            }
                          ]
                        }
                        """.formatted(warehouseId, idempotencyKey, presentationId))
            )
            .andReturn();
    }

    @SafeVarargs
    private final List<Integer> runConcurrently(Callable<MvcResult>... requests) throws Exception {
        List<Integer> statuses = runConcurrentlyResults(requests).stream()
            .map(result -> result.getResponse().getStatus())
            .sorted()
            .toList();
        return statuses;
    }

    @SafeVarargs
    private final List<MvcResult> runConcurrentlyResults(Callable<MvcResult>... requests) throws Exception {
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
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
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

    private UUID createReturnDraft(String token, ReturnFixture fixture) throws Exception {
        MvcResult createResult = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/returns", fixture.salesOrderId())
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "reason": "Regresion de cancelacion",
                          "items": [
                            {
                              "salesOrderItemId": "%s",
                              "quantity": 2
                            }
                          ]
                        }
                        """.formatted(fixture.salesOrderItemId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        return UUID.fromString(JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.returnId"));
    }

    private UUID createSupplier() {
        UUID supplierId = UUID.randomUUID();
        String suffix = supplierId.toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update(
            "INSERT INTO purchasing.suppliers (supplier_id, supplier_code, legal_name, status) VALUES (?, ?, ?, 'ACTIVE')",
            supplierId,
            "CON-S-" + suffix,
            "Concurrent Supplier"
        );
        return supplierId;
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
