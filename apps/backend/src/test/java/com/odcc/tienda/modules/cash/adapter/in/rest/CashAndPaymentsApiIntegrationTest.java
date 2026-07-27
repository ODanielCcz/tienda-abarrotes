package com.odcc.tienda.modules.cash.adapter.in.rest;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CashAndPaymentsApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldPayCashSaleReturnCleanPaymentResponseAndCloseCashSession() throws Exception {
        UUID userId = insertUser("cash_payment_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("cash_payment_admin", "correct-password");
        TestContext context = createContext("CASH-PAYMENT-FLOW");
        UUID orderId = insertSalesOrder(context, new BigDecimal("29.0000"), "CONFIRMED", "PENDING");

        MvcResult openResult = mockMvc.perform(
                post("/api/v1/cash/sessions/open")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashRegisterId": "%s",
                          "openingAmount": 1000.00,
                          "notes": "Apertura prueba automatica"
                        }
                        """.formatted(context.cashRegisterId()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CASH_SESSION_OPENED"))
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andReturn();

        String cashSessionId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.data.cashSessionId");

        MvcResult paymentResult = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashSessionId": "%s",
                          "paymentMethod": "CASH",
                          "amount": 29.00,
                          "currencyCode": "MXN",
                          "reference": "Pago efectivo automatizado",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(cashSessionId, UUID.randomUUID()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SALES_PAYMENT_CREATED"))
            .andExpect(jsonPath("$.data.cashSessionId").value(cashSessionId))
            .andExpect(jsonPath("$.data.currencyCode").value("MXN"))
            .andExpect(jsonPath("$.data.status").value("CAPTURED"))
            .andReturn();

        String paymentId = JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.data.paymentId");

        mockMvc.perform(
                get("/api/v1/sales/orders/{salesOrderId}", orderId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        mockMvc.perform(
                get("/api/v1/cash/sessions/{cashSessionId}/movements", cashSessionId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].direction").value("IN"))
            .andExpect(jsonPath("$.data[1].direction").value("IN"))
            .andExpect(jsonPath("$.data[1].paymentId").value(paymentId));

        mockMvc.perform(
                post("/api/v1/cash/sessions/{cashSessionId}/close", cashSessionId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "countedCashAmount": 1029.00,
                          "notes": "Cierre prueba automatica"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"))
            .andExpect(jsonPath("$.data.expectedAmount").value(1029.0000))
            .andExpect(jsonPath("$.data.countedAmount").value(1029.0000))
            .andExpect(jsonPath("$.data.differenceAmount").value(0.0000));

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN ('CASH_SESSION_OPENED', 'SALES_PAYMENT_CREATED', 'CASH_SESSION_CLOSED')
                """,
            Integer.class,
            userId
        );
        assertEquals(3, auditCount);
    }

    @Test
    void shouldReturnNullCashSessionForCardPaymentAndRejectOverpayment() throws Exception {
        insertUser("card_payment_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("card_payment_admin", "correct-password");
        TestContext context = createContext("CARD-PAYMENT");
        UUID orderId = insertSalesOrder(context, new BigDecimal("50.0000"), "CONFIRMED", "PENDING");

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "paymentMethod": "CARD",
                          "amount": 20.00,
                          "currencyCode": "MXN",
                          "reference": "Tarjeta prueba",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(UUID.randomUUID()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.cashSessionId").doesNotExist())
            .andExpect(jsonPath("$.data.currencyCode").value("MXN"));

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "paymentMethod": "CARD",
                          "amount": 40.00,
                          "currencyCode": "MXN",
                          "reference": "Sobrepago prueba",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(UUID.randomUUID()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SALES_PAYMENT_OVERPAID"));
    }

    @Test
    void shouldHandlePaymentIdempotency() throws Exception {
        insertUser("payment_idempotency_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("payment_idempotency_admin", "correct-password");
        TestContext context = createContext("PAYMENT-IDEMPOTENCY");
        UUID orderId = insertSalesOrder(context, new BigDecimal("100.0000"), "CONFIRMED", "PENDING");
        UUID idempotencyKey = UUID.randomUUID();

        String body = """
            {
              "paymentMethod": "CARD",
              "amount": 20.00,
              "currencyCode": "MXN",
              "reference": "Idempotente",
              "idempotencyKey": "%s"
            }
            """.formatted(idempotencyKey);

        MvcResult first = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isCreated())
            .andReturn();

        MvcResult second = mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String firstPaymentId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.paymentId");
        String secondPaymentId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.paymentId");
        assertEquals(firstPaymentId, secondPaymentId);

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "paymentMethod": "CARD",
                          "amount": 25.00,
                          "currencyCode": "MXN",
                          "reference": "Idempotente cambiado",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(idempotencyKey))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SALES_PAYMENT_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldRejectSecondOpenSessionAndCloseZeroCashWithoutArtificialMovement() throws Exception {
        insertUser("cash_zero_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("cash_zero_admin", "correct-password");
        TestContext context = createContext("CASH-ZERO");

        MvcResult openResult = mockMvc.perform(
                post("/api/v1/cash/sessions/open")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashRegisterId": "%s",
                          "openingAmount": 0.00,
                          "notes": "Apertura cero"
                        }
                        """.formatted(context.cashRegisterId()))
            )
            .andExpect(status().isCreated())
            .andReturn();
        String cashSessionId = JsonPath.read(openResult.getResponse().getContentAsString(), "$.data.cashSessionId");

        mockMvc.perform(
                post("/api/v1/cash/sessions/open")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashRegisterId": "%s",
                          "openingAmount": 10.00,
                          "notes": "Segunda apertura"
                        }
                        """.formatted(context.cashRegisterId()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CASH_SESSION_ALREADY_OPEN"));

        mockMvc.perform(
                post("/api/v1/cash/sessions/{cashSessionId}/close", cashSessionId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "countedCashAmount": 0.00,
                          "notes": "Cierre cero"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expectedAmount").value(0.0000))
            .andExpect(jsonPath("$.data.countedAmount").value(0.0000))
            .andExpect(jsonPath("$.data.differenceAmount").value(0.0000));

        Integer movements = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cash.cash_movements WHERE cash_session_id = ?",
            Integer.class,
            UUID.fromString(cashSessionId)
        );
        assertEquals(0, movements);
    }

    @Test
    void shouldProtectCashAndPaymentEndpoints() throws Exception {
        TestContext context = createContext("SECURITY-CASH-PAYMENT");
        UUID orderId = insertSalesOrder(context, new BigDecimal("10.0000"), "CONFIRMED", "PENDING");

        mockMvc.perform(post("/api/v1/cash/sessions/open"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        createRoleWithoutPermissions("TEST_NO_CASH_PAYMENT");
        insertUser("no_cash_payment", "correct-password", "TEST_NO_CASH_PAYMENT");
        String token = login("no_cash_payment", "correct-password");

        mockMvc.perform(
                post("/api/v1/cash/sessions/open")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "cashRegisterId": "%s",
                          "openingAmount": 1.00
                        }
                        """.formatted(context.cashRegisterId()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(
                post("/api/v1/sales/orders/{salesOrderId}/payments", orderId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "paymentMethod": "CARD",
                          "amount": 1.00,
                          "currencyCode": "MXN",
                          "idempotencyKey": "%s"
                        }
                        """.formatted(UUID.randomUUID()))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldExposeCashAndPaymentEndpointsInOpenApi() throws Exception {
        insertUser("openapi_cash_payment", "correct-password", "SYSTEM_ADMIN");
        String token = login("openapi_cash_payment", "correct-password");

        mockMvc.perform(
                get("/v3/api-docs")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/cash/sessions/open']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/orders/{salesOrderId}/payments']").exists());
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
            "Cash Payment Test " + username
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
                VALUES (?, ?, 'Rol de prueba sin permisos de caja/pagos', FALSE)
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

    private UUID insertSalesOrder(TestContext context, BigDecimal total, String status, String paymentStatus) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, channel, status,
                    payment_status, currency_code, subtotal, discount_total, tax_total, total,
                    idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, 'POS', ?, ?, 'MXN', ?, 0, 0, ?, ?, clock_timestamp())
                """,
            orderId,
            "SO-TEST-" + orderId.toString().substring(0, 8).toUpperCase(),
            context.branchId(),
            context.warehouseId(),
            status,
            paymentStatus,
            total,
            total,
            UUID.randomUUID()
        );
        return orderId;
    }

    private String code(String prefix, String type, String suffix) {
        String compactPrefix = prefix.replace("-", "");
        if (compactPrefix.length() > 16) {
            compactPrefix = compactPrefix.substring(0, 16);
        }
        return compactPrefix + type + suffix;
    }

    private record TestContext(UUID branchId, UUID warehouseId, UUID cashRegisterId) {
    }
}
