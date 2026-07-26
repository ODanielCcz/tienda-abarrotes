package com.odcc.tienda.shared.infrastructure.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.web.correlation.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgresBusinessAuditAdapter implements BusinessAuditPort {

    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void record(BusinessAuditEvent event) {
        RequestMetadata requestMetadata = currentRequestMetadata();
        ActorMetadata actorMetadata = currentActorMetadata();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("eventType", event.eventType())
            .addValue("aggregateType", event.aggregateType())
            .addValue("aggregateId", event.aggregateId())
            .addValue("beforeState", toJson(event.beforeState()))
            .addValue("afterState", toJson(event.afterState()))
            .addValue("metadata", toJson(event.metadata()))
            .addValue("actorUserId", actorMetadata.userId())
            .addValue("actorRole", actorMetadata.role())
            .addValue("correlationId", correlationId())
            .addValue("clientIp", requestMetadata.clientIp())
            .addValue("userAgent", requestMetadata.userAgent());

        jdbcTemplate.update(
            """
                INSERT INTO audit.business_events (
                    event_type,
                    aggregate_type,
                    aggregate_id,
                    before_state,
                    after_state,
                    metadata,
                    actor_user_id,
                    actor_role,
                    correlation_id,
                    client_ip,
                    user_agent
                )
                VALUES (
                    :eventType,
                    :aggregateType,
                    :aggregateId,
                    CAST(:beforeState AS jsonb),
                    CAST(:afterState AS jsonb),
                    CAST(:metadata AS jsonb),
                    :actorUserId,
                    :actorRole,
                    :correlationId,
                    CAST(:clientIp AS inet),
                    :userAgent
                )
                """,
            parameters
        );
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "No fue posible serializar el evento de auditoría",
                exception
            );
        }
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

    private static RequestMetadata currentRequestMetadata() {
        if (
            RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
        ) {
            HttpServletRequest request = attributes.getRequest();

            return new RequestMetadata(
                request.getRemoteAddr(),
                truncate(request.getHeader("User-Agent"))
            );
        }

        return new RequestMetadata(null, null);
    }

    private static ActorMetadata currentActorMetadata() {
        Authentication authentication = SecurityContextHolder
            .getContext()
            .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ActorMetadata.ANONYMOUS;
        }

        UUID userId = parseUserId(authentication.getName());
        String role = authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .sorted()
            .findFirst()
            .orElse(null);

        return new ActorMetadata(userId, role);
    }

    private static UUID parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_USER_AGENT_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_USER_AGENT_LENGTH);
    }

    private record RequestMetadata(String clientIp, String userAgent) {
    }

    private record ActorMetadata(UUID userId, String role) {

        private static final ActorMetadata ANONYMOUS =
            new ActorMetadata(null, null);
    }
}
