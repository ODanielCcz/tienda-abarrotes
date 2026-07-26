package com.odcc.tienda.modules.identity.adapter.out.bootstrap;

import com.odcc.tienda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles({"local", "test"})
@SpringBootTest
@TestPropertySource(properties = {
    "app.security.bootstrap-admin.enabled=true",
    "app.security.bootstrap-admin.username=bootstrap_admin_test",
    "app.security.bootstrap-admin.password=local-test-password-123",
    "app.security.bootstrap-admin.display-name=Administrador Bootstrap Test"
})
class LocalAdminBootstrapTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateLocalAdminWithEncodedPasswordRoleAndAudit() {
        var row = jdbcTemplate.queryForMap(
            """
                SELECT user_id, password_hash, display_name, status
                FROM iam.users
                WHERE username = 'bootstrap_admin_test'
                """
        );

        Integer assignedRole = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM iam.user_roles user_role
                JOIN iam.roles role ON role.role_id = user_role.role_id
                JOIN iam.users app_user ON app_user.user_id = user_role.user_id
                WHERE app_user.username = 'bootstrap_admin_test'
                  AND role.code = 'SYSTEM_ADMIN'
                """,
            Integer.class
        );

        Integer auditEvents = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM audit.business_events event
                JOIN iam.users app_user ON app_user.user_id = event.aggregate_id
                WHERE app_user.username = 'bootstrap_admin_test'
                  AND event.event_type = 'LOCAL_ADMIN_BOOTSTRAPPED'
                """,
            Integer.class
        );

        assertEquals("Administrador Bootstrap Test", row.get("display_name"));
        assertEquals("ACTIVE", row.get("status"));
        assertTrue(
            passwordEncoder.matches(
                "local-test-password-123",
                row.get("password_hash").toString()
            )
        );
        assertEquals(1, assignedRole);
        assertEquals(1, auditEvents);
    }
}
