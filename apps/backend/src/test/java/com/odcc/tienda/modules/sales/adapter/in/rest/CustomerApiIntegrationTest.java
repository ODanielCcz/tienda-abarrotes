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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldManageCustomerCrudAndFilters() throws Exception {
        UUID userId = insertUser("customer_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("customer_admin", "correct-password");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult created = mockMvc.perform(
                post("/api/v1/sales/customers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "customerCode": " cli-%s ",
                          "customerType": "person",
                          "displayName": "Cliente Prueba %s",
                          "email": "cliente%s@test.com",
                          "phone": "5551234567"
                        }
                        """.formatted(suffix, suffix, suffix.toLowerCase()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CUSTOMER_CREATED"))
            .andExpect(jsonPath("$.data.customerCode").value("CLI-" + suffix))
            .andExpect(jsonPath("$.data.customerType").value("PERSON"))
            .andReturn();

        String customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.customerId");

        mockMvc.perform(
                post("/api/v1/sales/customers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "customerCode": "CLI-%s",
                          "customerType": "PERSON",
                          "displayName": "Cliente Duplicado"
                        }
                        """.formatted(suffix))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CUSTOMER_CODE_ALREADY_EXISTS"));

        mockMvc.perform(
                get("/api/v1/sales/customers/{customerId}", customerId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CUSTOMER_FOUND"));

        mockMvc.perform(
                get("/api/v1/sales/customers")
                    .param("search", suffix)
                    .param("customerType", "PERSON")
                    .param("status", "ACTIVE")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].customerId").value(customerId));

        mockMvc.perform(
                put("/api/v1/sales/customers/{customerId}", customerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "customerCode": "CLI-%s-UPD",
                          "customerType": "BUSINESS",
                          "displayName": "Cliente Actualizado %s",
                          "email": "actualizado%s@test.com",
                          "phone": "5557654321"
                        }
                        """.formatted(suffix, suffix, suffix.toLowerCase()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CUSTOMER_UPDATED"))
            .andExpect(jsonPath("$.data.customerType").value("BUSINESS"));

        mockMvc.perform(
                patch("/api/v1/sales/customers/{customerId}/status", customerId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "status": "BLOCKED"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CUSTOMER_STATUS_UPDATED"))
            .andExpect(jsonPath("$.data.status").value("BLOCKED"));

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN ('CUSTOMER_CREATED', 'CUSTOMER_UPDATED', 'CUSTOMER_STATUS_CHANGED')
                """,
            Integer.class,
            userId
        );
        assertEquals(3, auditCount);
    }

    @Test
    void shouldRejectInvalidCustomerAndProtectEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/sales/customers"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        createRoleWithoutPermissions("TEST_NO_CUSTOMERS");
        insertUser("no_customers", "correct-password", "TEST_NO_CUSTOMERS");
        String noPermissionToken = login("no_customers", "correct-password");

        mockMvc.perform(
                post("/api/v1/sales/customers")
                    .header("Authorization", "Bearer " + noPermissionToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "displayName": "Cliente Sin Permiso"
                        }
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        insertUser("customer_validation_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("customer_validation_admin", "correct-password");

        mockMvc.perform(
                post("/api/v1/sales/customers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "customerType": "PERSON",
                          "displayName": " "
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectSalesOrderForInactiveCustomerAndListOrdersByCustomer() throws Exception {
        insertUser("customer_sales_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("customer_sales_admin", "correct-password");
        UUID activeCustomerId = insertCustomer("ACTIVE");
        UUID inactiveCustomerId = insertCustomer("INACTIVE");
        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        insertBranchAndWarehouse(branchId, warehouseId);
        UUID orderId = insertSalesOrder(branchId, warehouseId, activeCustomerId);

        mockMvc.perform(
                post("/api/v1/sales/orders")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "warehouseId": "%s",
                          "customerId": "%s",
                          "channel": "POS",
                          "currencyCode": "MXN",
                          "idempotencyKey": "%s",
                          "items": [
                            {
                              "productPresentationId": "%s",
                              "quantity": 1,
                              "unitPrice": 10.00,
                              "discountAmount": 0
                            }
                          ]
                        }
                        """.formatted(warehouseId, inactiveCustomerId, UUID.randomUUID(), UUID.randomUUID()))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_SALES_OPERATION"));

        mockMvc.perform(
                get("/api/v1/sales/orders")
                    .param("customerId", activeCustomerId.toString())
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].salesOrderId").value(orderId.toString()))
            .andExpect(jsonPath("$.data[0].customerId").value(activeCustomerId.toString()));
    }

    @Test
    void shouldExposeCustomerEndpointsInOpenApi() throws Exception {
        insertUser("openapi_customer", "correct-password", "SYSTEM_ADMIN");
        String token = login("openapi_customer", "correct-password");

        mockMvc.perform(
                get("/v3/api-docs")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/sales/customers']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/customers/{customerId}']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/sales/customers/{customerId}/status']").exists());
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
            "Customer Test " + username
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
                VALUES (?, ?, 'Rol de prueba sin permisos de clientes', FALSE)
                """,
            roleCode,
            roleCode
        );
    }

    private UUID insertCustomer(String status) {
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.customers (customer_id, customer_code, customer_type, display_name, status)
                VALUES (?, ?, 'PERSON', ?, ?)
                """,
            customerId,
            "CUST-" + customerId.toString().substring(0, 8).toUpperCase(),
            "Cliente " + status,
            status
        );
        return customerId;
    }

    private void insertBranchAndWarehouse(UUID branchId, UUID warehouseId) {
        String suffix = branchId.toString().substring(0, 8).toUpperCase();
        jdbcTemplate.update(
            """
                INSERT INTO organization.branches (branch_id, code, name, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
            branchId,
            "B" + suffix,
            "Branch " + suffix
        );
        jdbcTemplate.update(
            """
                INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            warehouseId,
            branchId,
            "W" + suffix,
            "Warehouse " + suffix
        );
    }

    private UUID insertSalesOrder(UUID branchId, UUID warehouseId, UUID customerId) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO sales.sales_orders (
                    sales_order_id, order_number, branch_id, warehouse_id, customer_id, channel, status,
                    payment_status, currency_code, subtotal, discount_total, tax_total, total,
                    idempotency_key, confirmed_at
                ) VALUES (?, ?, ?, ?, ?, 'POS', 'CONFIRMED', 'PENDING', 'MXN', 10, 0, 0, 10, ?, clock_timestamp())
                """,
            orderId,
            "SO-CUST-" + orderId.toString().substring(0, 8).toUpperCase(),
            branchId,
            warehouseId,
            customerId,
            UUID.randomUUID()
        );
        return orderId;
    }
}
