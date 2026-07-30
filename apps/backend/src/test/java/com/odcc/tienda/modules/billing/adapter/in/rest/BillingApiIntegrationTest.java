package com.odcc.tienda.modules.billing.adapter.in.rest;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateFiscalProfilesAndPreparePaidSaleDocument() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");
        String correlationId = "billing-flow-" + UUID.randomUUID();

        mockMvc.perform(put("/api/v1/catalog/products/{productId}/fiscal-classification", fixture.productId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"satProductServiceCode\":\"50131700\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reason").value("OK"))
            .andExpect(jsonPath("$.data.satProductServiceCode").value("50131700"));

        mockMvc.perform(put("/api/v1/catalog/units/{unitId}/fiscal-classification", fixture.unitId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"satUnitCode\":\"H87\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.satUnitCode").value("H87"));

        MvcResult issuerResult = mockMvc.perform(post("/api/v1/billing/issuer-profiles")
                .header("Authorization", "Bearer " + token)
                .header("X-Correlation-ID", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "branchId":"%s",
                      "rfc":"TAA010101AAA",
                      "legalName":"Tienda Abarrotes API",
                      "postalCode":"06000",
                      "fiscalRegimeCode":"601",
                      "defaultSeries":"A"
                    }
                    """.formatted(fixture.branchId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("ISSUER_PROFILE_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").value(correlationId))
            .andReturn();
        String issuerProfileId = JsonPath.read(issuerResult.getResponse().getContentAsString(), "$.data.issuerProfileId");

        MvcResult receiverResult = mockMvc.perform(post("/api/v1/billing/fiscal-profiles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId":"%s",
                      "rfc":"GODE561231GR8",
                      "legalName":"Cliente Fiscal Prueba",
                      "postalCode":"06000",
                      "fiscalRegimeCode":"605",
                      "cfdiUseCode":"G03",
                      "email":"cliente@example.com"
                    }
                    """.formatted(fixture.customerId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("FISCAL_PROFILE_CREATED"))
            .andReturn();
        String fiscalProfileId = JsonPath.read(receiverResult.getResponse().getContentAsString(), "$.data.fiscalProfileId");

        String documentPayload = """
            {
              "salesOrderId":"%s",
              "issuerProfileId":"%s",
              "fiscalProfileId":"%s",
              "folio":"1001",
              "paymentFormCode":"01",
              "paymentMethodCode":"PUE"
            }
            """.formatted(fixture.salesOrderId(), issuerProfileId, fiscalProfileId);

        MvcResult documentResult = mockMvc.perform(post("/api/v1/billing/fiscal-documents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_CREATED"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.issuerRfc").value("TAA010101AAA"))
            .andExpect(jsonPath("$.data.receiverRfc").value("GODE561231GR8"))
            .andExpect(jsonPath("$.data.items[0].satProductServiceCode").value("50131700"))
            .andExpect(jsonPath("$.data.items[0].satUnitCode").value("H87"))
            .andExpect(jsonPath("$.data.total").value(23.2))
            .andReturn();
        String documentId = JsonPath.read(documentResult.getResponse().getContentAsString(), "$.data.fiscalDocumentId");

        mockMvc.perform(post("/api/v1/billing/fiscal-documents/{documentId}/ready", documentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_READY"))
            .andExpect(jsonPath("$.data.status").value("READY"));

        mockMvc.perform(post("/api/v1/billing/fiscal-documents")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentPayload))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("BILLING_CONFLICT"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        Integer events = jdbc.queryForObject("""
            SELECT COUNT(*) FROM audit.business_events
            WHERE event_type IN ('FISCAL_DOCUMENT_CREATED','FISCAL_DOCUMENT_READY')
              AND aggregate_id = ?::uuid
            """, Integer.class, documentId);
        assertEquals(2, events);
    }

    @Test
    void shouldRejectInvalidRfcAndRequireAuthentication() throws Exception {
        Fixture fixture = fixture();
        String token = login(fixture.username(), "correct-password");

        mockMvc.perform(post("/api/v1/billing/issuer-profiles")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "branchId":"%s",
                      "rfc":"INVALIDO",
                      "legalName":"Emisor invalido",
                      "postalCode":"06000",
                      "fiscalRegimeCode":"601"
                    }
                    """.formatted(fixture.branchId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_BILLING_OPERATION"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());

        mockMvc.perform(get("/api/v1/billing/fiscal-documents"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID userId = UUID.randomUUID();
        String username = "billing_" + suffix;
        jdbc.update("""
            INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """, userId, username, passwordEncoder.encode("correct-password"), "Billing Test");
        jdbc.update("""
            INSERT INTO iam.user_roles (user_id, role_id)
            SELECT ?, role_id FROM iam.roles WHERE code = 'SYSTEM_ADMIN'
            """, userId);

        UUID branchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID presentationId = UUID.randomUUID();
        UUID salesOrderId = UUID.randomUUID();
        UUID salesOrderItemId = UUID.randomUUID();

        jdbc.update("INSERT INTO organization.branches (branch_id, code, name, legal_name) VALUES (?, ?, ?, ?)",
            branchId, "BILL-" + suffix, "Sucursal Billing", "Sucursal Billing Legal");
        jdbc.update("INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name) VALUES (?, ?, ?, ?)",
            warehouseId, branchId, "WH-" + suffix, "Almacen Billing");
        jdbc.update("INSERT INTO sales.customers (customer_id, customer_code, customer_type, display_name, status) VALUES (?, ?, 'PERSON', ?, 'ACTIVE')",
            customerId, "CUS-" + suffix, "Cliente Fiscal Prueba");
        jdbc.update("INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol) VALUES (?, ?, ?, ?)",
            unitId, "U-" + suffix, "Pieza Billing", "pza");
        jdbc.update("""
            INSERT INTO catalog.products (
                product_id, name, product_type, tracks_inventory, tracks_lots, tracks_expiration, status
            ) VALUES (?, ?, 'GOODS', TRUE, FALSE, FALSE, 'ACTIVE')
            """, productId, "Leche de prueba");
        jdbc.update("""
            INSERT INTO catalog.product_presentations (
                product_presentation_id, product_id, unit_id, sku, name, status
            ) VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """, presentationId, productId, unitId, "SKU-" + suffix, "Presentacion Billing");
        jdbc.update("""
            INSERT INTO sales.sales_orders (
                sales_order_id, order_number, branch_id, warehouse_id, customer_id,
                channel, status, payment_status, currency_code, subtotal,
                discount_total, tax_total, total, idempotency_key, created_by, confirmed_at
            ) VALUES (?, ?, ?, ?, ?, 'POS', 'CONFIRMED', 'PAID', 'MXN', 20, 0, 3.2, 23.2, ?, ?, clock_timestamp())
            """, salesOrderId, "SO-" + suffix, branchId, warehouseId, customerId, UUID.randomUUID(), userId);
        jdbc.update("""
            INSERT INTO sales.sales_order_items (
                sales_order_item_id, sales_order_id, product_presentation_id,
                product_name_snapshot, sku_snapshot, quantity, unit_price, unit_cost,
                discount_amount, tax_rate, tax_amount, line_total
            ) VALUES (?, ?, ?, ?, ?, 1, 20, 12, 0, 0.16, 3.2, 23.2)
            """, salesOrderItemId, salesOrderId, presentationId, "Leche de prueba", "SKU-" + suffix);
        return new Fixture(username, branchId, customerId, unitId, productId, salesOrderId);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s"}
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private record Fixture(
        String username,
        UUID branchId,
        UUID customerId,
        UUID unitId,
        UUID productId,
        UUID salesOrderId
    ) {
    }
}
