package com.odcc.tienda.shared.security;

import com.jayway.jsonpath.JsonPath;
import com.odcc.tienda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@AutoConfigureTracing(export = false)
@Transactional
class SecurityIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String BRANDS_ENDPOINT = "/api/v1/catalog/brands";
    private static final String CATEGORIES_ENDPOINT = "/api/v1/catalog/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Tracer tracer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void shouldLoginWithDatabaseUserAndUseRealJwt() throws Exception {
        UUID userId = insertUser(
            "security_manager",
            "correct-password",
            "CATALOG_MANAGER"
        );

        String token = login("security_manager", "correct-password");

        mockMvc.perform(
                get(BRANDS_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("BRANDS_FOUND"));

        mockMvc.perform(
                get(CATEGORIES_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CATEGORIES_FOUND"));

        Integer successfulLogins = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.user_security_events
                WHERE user_id = ? AND event_type = 'LOGIN_SUCCESS'
                """,
            Integer.class,
            userId
        );

        assertEquals(1, successfulLogins);
    }

    @Test
    void shouldReturnStructuredUnauthorizedResponseWithoutToken()
        throws Exception {
        mockMvc.perform(
                get(CATEGORIES_ENDPOINT)
                    .header("X-Correlation-ID", "security-unauthorized")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Correlation-ID", "security-unauthorized"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldRejectCorrectlySignedTokenFromUnexpectedIssuer()
        throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("another-application")
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("authorities", List.of("CATALOG_BRAND_READ"))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder
            .encode(JwtEncoderParameters.from(header, claims))
            .getTokenValue();

        mockMvc.perform(
                get(BRANDS_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturnForbiddenWhenTokenLacksCreatePermission()
        throws Exception {
        createReadOnlyRole();
        insertUser(
            "security_reader",
            "correct-password",
            "TEST_BRAND_READER"
        );
        String token = login("security_reader", "correct-password");

        mockMvc.perform(
                post(BRANDS_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"code": "FORBIDDEN", "name": "Sin permiso"}
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(
                post(CATEGORIES_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"code": "FORBIDDEN-CATEGORY", "name": "Sin permiso"}
                        """)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAuditInvalidPassword() throws Exception {
        UUID userId = insertUser(
            "security_failed",
            "correct-password",
            "CATALOG_MANAGER"
        );

        mockMvc.perform(
                post(LOGIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "username": "security_failed",
                          "password": "wrong-password"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        Integer failedLogins = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.user_security_events
                WHERE user_id = ?
                  AND event_type = 'LOGIN_FAILURE'
                  AND details ->> 'reason' = 'INVALID_PASSWORD'
                """,
            Integer.class,
            userId
        );

        assertEquals(1, failedLogins);
    }

    @Test
    void shouldAuditAuthenticatedActorOnBrandChange() throws Exception {
        UUID userId = insertUser(
            "security_audit_actor",
            "correct-password",
            "CATALOG_MANAGER"
        );
        String token = login("security_audit_actor", "correct-password");

        MvcResult result = mockMvc.perform(
                post(BRANDS_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-ID", "security-audit-actor")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "code": "SECURITY-AUDIT-ACTOR",
                          "name": "Marca con actor auditado"
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andReturn();

        String brandId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.data.id"
        );
        Map<String, Object> event = jdbcTemplate.queryForMap(
            """
                SELECT actor_user_id, actor_role
                FROM audit.business_events
                WHERE aggregate_id = ? AND event_type = 'BRAND_CREATED'
                """,
            UUID.fromString(brandId)
        );

        assertEquals(userId, event.get("actor_user_id"));
        assertEquals("CATALOG_MANAGER", event.get("actor_role"));
    }

    @Test
    void shouldAllowConfiguredCorsPreflight() throws Exception {
        mockMvc.perform(
                options(BRANDS_ENDPOINT)
                    .header("Origin", "http://localhost:4200")
                    .header("Access-Control-Request-Method", "GET")
                    .header(
                        "Access-Control-Request-Headers",
                        "Authorization,Content-Type"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    "Access-Control-Allow-Origin",
                    "http://localhost:4200"
                )
            )
            .andExpect(
                header().string(
                    "Access-Control-Allow-Credentials",
                    "true"
                )
            );
    }

    @Test
    void shouldExposePrometheusToAuthenticatedUser() throws Exception {
        insertUser(
            "security_observer",
            "correct-password",
            "CATALOG_MANAGER"
        );
        String token = login("security_observer", "correct-password");

        mockMvc.perform(
                get("/actuator/prometheus")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string(containsString("# HELP")));
    }

    @Test
    void shouldCreateOpenTelemetryTraceContext() {
        Span span = tracer.nextSpan().name("observability-integration-test").start();

        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertFalse(span.context().traceId().isBlank());
            assertFalse(span.context().spanId().isBlank());
        } finally {
            span.end();
        }
    }

    @Test
    void shouldReturnStructuredErrorsForUnknownRouteAndMethod()
        throws Exception {
        insertUser(
            "security_errors",
            "correct-password",
            "CATALOG_MANAGER"
        );
        String token = login("security_errors", "correct-password");

        mockMvc.perform(
                get("/api/v1/unknown-route")
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(
                delete(BRANDS_ENDPOINT)
                    .header("Authorization", "Bearer " + token)
            )
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                post(LOGIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                            {
                              "username": "%s",
                              "password": "%s"
                            }
                            """.formatted(username, password)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn();

        return JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.data.accessToken"
        );
    }

    private UUID insertUser(
        String username,
        String password,
        String roleCode
    ) {
        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                INSERT INTO iam.users (
                    user_id, username, password_hash, display_name, status
                )
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            userId,
            username,
            passwordEncoder.encode(password),
            "Security Test " + username
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

    private void createReadOnlyRole() {
        jdbcTemplate.update(
            """
                INSERT INTO iam.roles (code, name, description, is_system)
                VALUES (
                    'TEST_BRAND_READER',
                    'Lector de marcas de prueba',
                    'Rol temporal para pruebas de autorización',
                    FALSE
                )
                """
        );

        jdbcTemplate.update(
            """
                INSERT INTO iam.role_permissions (role_id, permission_id)
                SELECT role.role_id, permission.permission_id
                FROM iam.roles role
                JOIN iam.permissions permission
                  ON permission.code = 'CATALOG_BRAND_READ'
                WHERE role.code = 'TEST_BRAND_READER'
                """
        );
    }
}
