package com.odcc.tienda.modules.identity.adapter.out.audit;

import com.odcc.tienda.modules.identity.application.port.out.AuthenticationAuditPort;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresAuthenticationAuditAdapter
    implements AuthenticationAuditPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void loginSucceeded(UUID userId, String username) {
        append(userId, username, "LOGIN_SUCCESS", "SUCCESS", "AUTHENTICATED");
    }

    @Override
    public void loginFailed(UUID userId, String username, String reason) {
        append(userId, username, "LOGIN_FAILURE", "FAILURE", reason);
    }

    private void append(
        UUID userId,
        String username,
        String eventType,
        String outcome,
        String reason
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("eventType", eventType)
            .addValue("outcome", outcome)
            .addValue("correlationId", correlationId())
            .addValue("username", username)
            .addValue("reason", reason);

        jdbcTemplate.update(
            """
                INSERT INTO audit.user_security_events (
                    user_id,
                    event_type,
                    outcome,
                    correlation_id,
                    details
                )
                VALUES (
                    :userId,
                    :eventType,
                    :outcome,
                    :correlationId,
                    jsonb_build_object(
                        'username', :username,
                        'reason', :reason
                    )
                )
                """,
            parameters
        );
    }

    private static UUID correlationId() {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);

        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8)
            );
        }
    }
}
