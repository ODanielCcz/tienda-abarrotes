package com.odcc.tienda.modules.identity.adapter.out.bootstrap;

import com.odcc.tienda.modules.identity.application.port.out.PasswordPolicyPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalAdminBootstrap implements ApplicationRunner {

    private final BootstrapAdminProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final BusinessAuditPort auditPort;
    private final PasswordPolicyPort passwordPolicyPort;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) {
            return;
        }

        validateProperties();

        transactionTemplate.executeWithoutResult(status -> createIfMissing());
    }

    private void createIfMissing() {
        String username = properties.username()
            .trim()
            .toLowerCase(Locale.ROOT);

        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.users WHERE LOWER(username) = LOWER(?)",
            Integer.class,
            username
        );

        if (existing != null && existing > 0) {
            log.info("El usuario administrador local ya existe: {}", username);
            return;
        }

        passwordPolicyPort.validate(username, properties.password());

        UUID userId = UUID.randomUUID();

        jdbcTemplate.update(
            """
                INSERT INTO iam.users (
                    user_id,
                    username,
                    password_hash,
                    display_name,
                    status,
                    password_changed_at
                )
                VALUES (?, ?, ?, ?, 'ACTIVE', clock_timestamp())
                """,
            userId,
            username,
            passwordEncoder.encode(properties.password()),
            properties.displayName().trim()
        );

        int assignedRoles = jdbcTemplate.update(
            """
                INSERT INTO iam.user_roles (user_id, role_id)
                SELECT ?, role_id
                FROM iam.roles
                WHERE code = 'SYSTEM_ADMIN' AND status = 'ACTIVE'
                """,
            userId
        );

        if (assignedRoles != 1) {
            throw new IllegalStateException(
                "No fue posible asignar el rol SYSTEM_ADMIN"
            );
        }

        auditPort.record(
            new BusinessAuditEvent(
                "LOCAL_ADMIN_BOOTSTRAPPED",
                "USER",
                userId,
                Map.of(),
                Map.of(
                    "username", username,
                    "displayName", properties.displayName().trim(),
                    "status", "ACTIVE",
                    "role", "SYSTEM_ADMIN"
                ),
                Map.of("source", "LOCAL_BOOTSTRAP")
            )
        );

        log.info("Usuario administrador local creado: {}", username);
    }

    private void validateProperties() {
        if (properties.username() == null || properties.username().isBlank()) {
            throw new IllegalStateException(
                "BOOTSTRAP_ADMIN_USERNAME es obligatorio cuando el bootstrap está habilitado"
            );
        }

        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException(
                "BOOTSTRAP_ADMIN_PASSWORD es obligatorio cuando el bootstrap está habilitado"
            );
        }

        if (properties.displayName() == null || properties.displayName().isBlank()) {
            throw new IllegalStateException(
                "BOOTSTRAP_ADMIN_DISPLAY_NAME es obligatorio cuando el bootstrap está habilitado"
            );
        }
    }
}
