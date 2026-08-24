package com.odcc.tienda.modules.identity.adapter.out.persistence;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class UserAccountPersistenceAdapterTest {

    @Autowired
    private UserAccountPort userAccountPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldLoadUserWithActiveRolesAndPermissions() {
        UUID userId = UUID.fromString(
            "0726681e-f4ce-4f1f-a0aa-2347ce8e7cb2"
        );
        insertUser(userId, "identity_test");
        assignRole(userId, "CATALOG_MANAGER");

        UserAccount user = userAccountPort
            .findByUsername("IDENTITY_TEST")
            .orElseThrow();

        assertEquals(userId, user.id());
        assertEquals(UserAccountStatus.ACTIVE, user.status());
        assertEquals("Identity Test", user.displayName());
        assertTrue(user.roles().contains("CATALOG_MANAGER"));
        assertTrue(user.permissions().contains("CATALOG_BRAND_READ"));
        assertTrue(user.permissions().contains("CATALOG_BRAND_CREATE"));
    }

    @Test
    void shouldReturnEmptyForUnknownUsername() {
        Optional<UserAccount> user = userAccountPort.findByUsername("unknown");

        assertTrue(user.isEmpty());
    }

    @Test
    void shouldPersistFifteenMinuteLockAtFifthFailure() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, "locked_identity_test");
        Instant lockedUntil = Instant.parse("2026-08-23T18:15:00Z");

        for (int attempt = 0; attempt < 5; attempt++) {
            userAccountPort.recordFailedLogin(userId, lockedUntil);
        }

        UserAccount user = userAccountPort
            .findByUsername("locked_identity_test")
            .orElseThrow();
        assertEquals(5, user.failedLoginAttempts());
        assertEquals(lockedUntil, user.lockedUntil());
    }

    private void insertUser(UUID userId, String username) {
        jdbcTemplate.update(
            """
                INSERT INTO iam.users (
                    user_id, username, password_hash, display_name, status
                )
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """,
            userId,
            username,
            "$2a$10$test.hash.for.persistence.adapter.only",
            "Identity Test"
        );
    }

    private void assignRole(UUID userId, String roleCode) {
        jdbcTemplate.update(
            """
                INSERT INTO iam.user_roles (user_id, role_id)
                SELECT ?, role_id FROM iam.roles WHERE code = ?
                """,
            userId,
            roleCode
        );
    }
}
