package com.odcc.tienda.shared.infrastructure.audit;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class PostgresBusinessAuditAdapterTest {

    @Autowired
    private BusinessAuditPort auditPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldAppendBusinessEventToPostgreSql() {
        UUID aggregateId = UUID.fromString(
            "60d51631-441b-4b35-b1b0-7cf1d5d26bbb"
        );

        UUID correlationId = UUID.fromString(
            "6874e4fc-dc21-44e0-a97c-1c0932414562"
        );
        UUID actorUserId = UUID.fromString(
            "0c679993-0984-4394-863c-60dab8f1d190"
        );

        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId.toString());
        SecurityContextHolder.getContext().setAuthentication(
            authenticatedUser(actorUserId)
        );

        try {
            auditPort.record(
                new BusinessAuditEvent(
                    "BRAND_TESTED",
                    "BRAND",
                    aggregateId,
                    Map.of("status", "ACTIVE"),
                    Map.of("status", "INACTIVE"),
                    Map.of("source", "integration-test")
                )
            );
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
            SecurityContextHolder.clearContext();
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
                SELECT event_type, aggregate_type, aggregate_id,
                       before_state, after_state, metadata, correlation_id,
                       actor_user_id, actor_role
                FROM audit.business_events
                WHERE aggregate_id = ? AND event_type = 'BRAND_TESTED'
                """,
            aggregateId
        );

        assertEquals("BRAND_TESTED", row.get("event_type"));
        assertEquals("BRAND", row.get("aggregate_type"));
        assertEquals(aggregateId, row.get("aggregate_id"));
        assertNotNull(row.get("before_state"));
        assertNotNull(row.get("after_state"));
        assertNotNull(row.get("metadata"));
        assertEquals(correlationId, row.get("correlation_id"));
        assertEquals(actorUserId, row.get("actor_user_id"));
        assertEquals("CATALOG_MANAGER", row.get("actor_role"));
    }

    @Test
    void shouldRejectMutationOfBusinessAuditEvent() {
        UUID aggregateId = UUID.randomUUID();

        auditPort.record(
            new BusinessAuditEvent(
                "BRAND_APPEND_ONLY_TESTED",
                "BRAND",
                aggregateId,
                Map.of(),
                Map.of("status", "ACTIVE"),
                Map.of()
            )
        );

        assertThrows(
            DataAccessException.class,
            () -> jdbcTemplate.update(
                """
                    UPDATE audit.business_events
                    SET event_type = 'MUTATED'
                    WHERE aggregate_id = ?
                    """,
                aggregateId
            )
        );
    }

    private static JwtAuthenticationToken authenticatedUser(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("audit-test-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("authorities", List.of("ROLE_CATALOG_MANAGER"))
            .build();

        return new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
        );
    }
}
