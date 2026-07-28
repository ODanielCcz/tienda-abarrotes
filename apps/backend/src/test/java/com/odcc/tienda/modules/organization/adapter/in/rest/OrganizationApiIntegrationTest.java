package com.odcc.tienda.modules.organization.adapter.in.rest;

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
class OrganizationApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldManageOrganizationCatalogsAndAuditChanges() throws Exception {
        UUID userId = insertUser("organization_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("organization_admin", "correct-password");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult branchResult = mockMvc.perform(
                post("/api/v1/organization/branches")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "organization-flow-" + suffix)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "br-%s",
                          "name": "Sucursal %s",
                          "legalName": "Sucursal Legal %s",
                          "timezone": "America/Mexico_City",
                          "currencyCode": "mxn"
                        }
                        """.formatted(suffix, suffix, suffix))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("BRANCH_CREATED"))
            .andExpect(jsonPath("$.reason").value("Created"))
            .andExpect(jsonPath("$.correlationId").value("organization-flow-" + suffix))
            .andExpect(jsonPath("$.data.code").value("BR-" + suffix))
            .andExpect(jsonPath("$.data.currencyCode").value("MXN"))
            .andReturn();
        String branchId = JsonPath.read(branchResult.getResponse().getContentAsString(), "$.data.branchId");

        mockMvc.perform(
                get("/api/v1/organization/branches/{branchId}", branchId)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRANCH_FOUND"))
            .andExpect(jsonPath("$.reason").value("OK"));

        mockMvc.perform(
                put("/api/v1/organization/branches/{branchId}", branchId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "br-%s-upd",
                          "name": "Sucursal Actualizada %s",
                          "legalName": "Sucursal Legal Actualizada %s",
                          "timezone": "America/Mexico_City",
                          "currencyCode": "MXN"
                        }
                        """.formatted(suffix, suffix, suffix))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRANCH_UPDATED"))
            .andExpect(jsonPath("$.data.code").value("BR-" + suffix + "-UPD"));

        MvcResult warehouseResult = mockMvc.perform(
                post("/api/v1/organization/warehouses")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "branchId": "%s",
                          "code": "wh-%s",
                          "name": "Almacen %s",
                          "warehouseType": "STORE"
                        }
                        """.formatted(branchId, suffix, suffix))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_CREATED"))
            .andExpect(jsonPath("$.data.code").value("WH-" + suffix))
            .andReturn();
        String warehouseId = JsonPath.read(warehouseResult.getResponse().getContentAsString(), "$.data.warehouseId");

        MvcResult deviceResult = mockMvc.perform(
                post("/api/v1/organization/devices")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "branchId": "%s",
                          "warehouseId": "%s",
                          "deviceCode": "pos-%s",
                          "deviceType": "POS",
                          "platform": "DOCKER-TEST",
                          "appVersion": "1.0.0"
                        }
                        """.formatted(branchId, warehouseId, suffix))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("DEVICE_CREATED"))
            .andExpect(jsonPath("$.data.deviceCode").value("POS-" + suffix))
            .andReturn();
        String deviceId = JsonPath.read(deviceResult.getResponse().getContentAsString(), "$.data.deviceId");

        MvcResult registerResult = mockMvc.perform(
                post("/api/v1/organization/cash-registers")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "branchId": "%s",
                          "deviceId": "%s",
                          "code": "cash-%s",
                          "name": "Caja %s"
                        }
                        """.formatted(branchId, deviceId, suffix, suffix))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CASH_REGISTER_CREATED"))
            .andExpect(jsonPath("$.data.code").value("CASH-" + suffix))
            .andReturn();
        String cashRegisterId = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.data.cashRegisterId");

        mockMvc.perform(get("/api/v1/organization/warehouses").param("branchId", branchId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WAREHOUSES_FOUND"));
        mockMvc.perform(get("/api/v1/organization/devices/{deviceId}", deviceId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DEVICE_FOUND"));
        mockMvc.perform(get("/api/v1/organization/cash-registers/{cashRegisterId}", cashRegisterId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CASH_REGISTER_FOUND"));

        mockMvc.perform(
                patch("/api/v1/organization/warehouses/{warehouseId}/status", warehouseId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{" + "\"status\":\"INACTIVE\"" + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("WAREHOUSE_STATUS_UPDATED"));

        mockMvc.perform(
                patch("/api/v1/organization/devices/{deviceId}/status", deviceId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{" + "\"status\":\"BLOCKED\"" + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("DEVICE_STATUS_UPDATED"));

        mockMvc.perform(
                patch("/api/v1/organization/cash-registers/{cashRegisterId}/status", cashRegisterId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{" + "\"status\":\"MAINTENANCE\"" + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CASH_REGISTER_STATUS_UPDATED"));

        mockMvc.perform(
                patch("/api/v1/organization/branches/{branchId}/status", branchId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{" + "\"status\":\"INACTIVE\"" + "}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRANCH_STATUS_UPDATED"));

        mockMvc.perform(
                post("/api/v1/organization/warehouses")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "branchId": "%s",
                          "code": "wh-blocked-%s",
                          "name": "Almacen bloqueado %s",
                          "warehouseType": "STORE"
                        }
                        """.formatted(branchId, suffix, suffix))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_ORGANIZATION_OPERATION"))
            .andExpect(jsonPath("$.reason").value("Bad Request"))
            .andExpect(jsonPath("$.correlationId").exists());

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN (
                      'BRANCH_CREATED', 'BRANCH_UPDATED', 'BRANCH_STATUS_CHANGED',
                      'WAREHOUSE_CREATED', 'WAREHOUSE_STATUS_CHANGED',
                      'DEVICE_CREATED', 'DEVICE_STATUS_CHANGED',
                      'CASH_REGISTER_CREATED', 'CASH_REGISTER_STATUS_CHANGED'
                  )
                """,
            Integer.class,
            userId
        );
        assertEquals(9, auditCount);
    }

    @Test
    void shouldRejectDuplicateCodesAndProtectOrganizationEndpoints() throws Exception {
        insertUser("organization_security_admin", "correct-password", "SYSTEM_ADMIN");
        String token = login("organization_security_admin", "correct-password");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult branchResult = mockMvc.perform(
                post("/api/v1/organization/branches")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "dup-%s",
                          "name": "Sucursal Duplicada %s"
                        }
                        """.formatted(suffix, suffix))
            )
            .andExpect(status().isCreated())
            .andReturn();
        String branchId = JsonPath.read(branchResult.getResponse().getContentAsString(), "$.data.branchId");

        mockMvc.perform(
                post("/api/v1/organization/branches")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "DUP-%s",
                          "name": "Sucursal Duplicada 2 %s"
                        }
                        """.formatted(suffix, suffix))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.reason").value("Conflict"));

        mockMvc.perform(get("/api/v1/organization/branches"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.reason").value("Unauthorized"))
            .andExpect(jsonPath("$.correlationId").exists());

        createRoleWithoutPermissions("NO_ORGANIZATION_PERMISSION");
        insertUser("organization_no_permission", "correct-password", "NO_ORGANIZATION_PERMISSION");
        String noPermissionToken = login("organization_no_permission", "correct-password");

        mockMvc.perform(get("/api/v1/organization/branches").header("Authorization", "Bearer " + noPermissionToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.reason").value("Forbidden"))
            .andExpect(jsonPath("$.correlationId").exists());

        mockMvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/organization/branches']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/organization/branches/{branchId}']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/organization/warehouses']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/organization/cash-registers']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/organization/devices']").exists());

        mockMvc.perform(get("/api/v1/organization/branches/{branchId}", branchId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
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
            "Organization Test " + username
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
                VALUES (?, ?, 'Rol de prueba sin permisos de organizacion', FALSE)
                """,
            roleCode,
            roleCode
        );
    }
}