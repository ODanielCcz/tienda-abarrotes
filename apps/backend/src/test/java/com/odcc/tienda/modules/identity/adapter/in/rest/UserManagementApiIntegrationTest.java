package com.odcc.tienda.modules.identity.adapter.in.rest;

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
class UserManagementApiIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldManageUsersRolesPasswordAndLoginLifecycle() throws Exception {
        UUID adminId = insertUser("identity_admin", "correct-password", "SYSTEM_ADMIN", "ACTIVE");
        String adminToken = login("identity_admin", "correct-password");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "cajero_api_" + suffix;

        MvcResult created = mockMvc.perform(
                post("/api/v1/identity/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": " %s ",
                          "displayName": "Cajero API",
                          "password": "Temporal123!",
                          "roleCodes": ["SYSTEM_ADMIN"]
                        }
                        """.formatted(username.toUpperCase()))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_CREATED"))
            .andExpect(jsonPath("$.data.username").value(username))
            .andExpect(jsonPath("$.data.roles[0]").value("SYSTEM_ADMIN"))
            .andReturn();

        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.userId");

        mockMvc.perform(
                post("/api/v1/identity/users")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "%s",
                          "displayName": "Duplicado",
                          "password": "Temporal123!",
                          "roleCodes": ["SYSTEM_ADMIN"]
                        }
                        """.formatted(username))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_ALREADY_EXISTS"));

        login(username, "Temporal123!");

        mockMvc.perform(
                get("/api/v1/identity/users/{userId}", userId)
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_FOUND"))
            .andExpect(jsonPath("$.data.userId").value(userId));

        mockMvc.perform(
                get("/api/v1/identity/users")
                    .param("search", username.substring(0, 12))
                    .param("status", "ACTIVE")
                    .param("roleCode", "SYSTEM_ADMIN")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USERS_FOUND"))
            .andExpect(jsonPath("$.data[0].userId").exists());

        mockMvc.perform(
                put("/api/v1/identity/users/{userId}", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "%s_upd",
                          "displayName": "Cajero API Actualizado"
                        }
                        """.formatted(username))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_UPDATED"))
            .andExpect(jsonPath("$.data.username").value(username + "_upd"));

        mockMvc.perform(
                put("/api/v1/identity/users/{userId}/roles", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "roleCodes": ["SYSTEM_ADMIN"]
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_ROLES_UPDATED"));

        mockMvc.perform(
                put("/api/v1/identity/users/{userId}/roles", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "roleCodes": ["ROL_QUE_NO_EXISTE"]
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IDENTITY_ROLE_NOT_FOUND"));

        mockMvc.perform(
                post("/api/v1/identity/users/{userId}/password", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "password": "NuevaTemporal123!"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_PASSWORD_CHANGED"));

        login(username + "_upd", "NuevaTemporal123!");

        mockMvc.perform(
                patch("/api/v1/identity/users/{userId}/status", userId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "status": "DISABLED"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_USER_STATUS_UPDATED"))
            .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(
                post(LOGIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "%s_upd",
                          "password": "NuevaTemporal123!"
                        }
                        """.formatted(username))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_NOT_ACTIVE"));
        mockMvc.perform(
                patch("/api/v1/identity/users/{userId}/status", adminId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "status": "DISABLED"
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_SELF_DISABLE_NOT_ALLOWED"));

        Integer auditCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events
                WHERE actor_user_id = ?
                  AND event_type IN ('USER_CREATED', 'USER_UPDATED', 'USER_ROLES_UPDATED', 'USER_PASSWORD_CHANGED', 'USER_STATUS_CHANGED')
                """,
            Integer.class,
            adminId
        );
        assertEquals(5, auditCount);
    }

    @Test
    void shouldProtectIdentityEndpointsAndExposeOpenApi() throws Exception {
        mockMvc.perform(get("/api/v1/identity/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        createRoleWithoutPermissions("TEST_NO_IDENTITY");
        insertUser("identity_no_permissions", "correct-password", "TEST_NO_IDENTITY", "ACTIVE");
        String noPermissionToken = login("identity_no_permissions", "correct-password");

        mockMvc.perform(
                get("/api/v1/identity/users")
                    .header("Authorization", "Bearer " + noPermissionToken)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        insertUser("identity_openapi_admin", "correct-password", "SYSTEM_ADMIN", "ACTIVE");
        String adminToken = login("identity_openapi_admin", "correct-password");

        mockMvc.perform(
                get("/api/v1/identity/roles")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_ROLES_FOUND"));

        mockMvc.perform(
                get("/api/v1/identity/permissions")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("IDENTITY_PERMISSIONS_FOUND"));

        mockMvc.perform(
                get("/v3/api-docs")
                    .header("Authorization", "Bearer " + adminToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/identity/users']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/users/{userId}']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/users/{userId}/status']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/users/{userId}/password']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/users/{userId}/roles']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/roles']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/identity/permissions']").exists());
    }

    @Test
    void shouldRejectRemovingLastSystemAdminRole() throws Exception {
        UUID adminId = insertUser("identity_only_admin", "correct-password", "SYSTEM_ADMIN", "ACTIVE");
        String adminToken = login("identity_only_admin", "correct-password");

        mockMvc.perform(
                put("/api/v1/identity/users/{userId}/roles", adminId)
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "roleCodes": []
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDENTITY_LAST_SYSTEM_ADMIN"));
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

    private UUID insertUser(String username, String password, String roleCode, String status) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO iam.users (user_id, username, password_hash, display_name, status)
                VALUES (?, ?, ?, ?, ?)
                """,
            userId,
            username,
            passwordEncoder.encode(password),
            "Identity Test " + username,
            status
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
                VALUES (?, ?, 'Rol de prueba sin permisos de identidad', FALSE)
                """,
            roleCode,
            roleCode
        );
    }
}
