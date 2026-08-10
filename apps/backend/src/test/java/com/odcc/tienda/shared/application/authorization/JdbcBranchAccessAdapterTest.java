package com.odcc.tienda.shared.application.authorization;

import com.odcc.tienda.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class JdbcBranchAccessAdapterTest {

    @Autowired
    private BranchAccessPort branchAccessPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void systemAdministratorHasGlobalAccessToActiveBranches() {
        UUID userId = insertUserWithRole("branch_global_admin", "SYSTEM_ADMIN");
        UUID branchId = insertBranch("GLOBAL");

        BranchScope scope = branchAccessPort.resolveScope(userId);

        assertTrue(scope.globalAccess());
        assertDoesNotThrow(() -> branchAccessPort.requireAccess(userId, branchId));
    }

    @Test
    void regularUserOnlyHasAccessToExplicitlyAssignedActiveBranches() {
        UUID userId = insertUserWithRole("branch_scoped_user", "CATALOG_MANAGER");
        UUID allowedBranchId = insertBranch("ALLOWED");
        UUID deniedBranchId = insertBranch("DENIED");
        assignBranch(userId, allowedBranchId, "ACTIVE");

        BranchScope scope = branchAccessPort.resolveScope(userId);

        assertFalse(scope.globalAccess());
        assertEquals(Set.of(allowedBranchId), scope.branchIds());
        assertDoesNotThrow(() -> branchAccessPort.requireAccess(userId, allowedBranchId));
        assertThrows(
            BranchAccessDeniedException.class,
            () -> branchAccessPort.requireAccess(userId, deniedBranchId)
        );
    }

    @Test
    void inactiveBranchIsDeniedEvenForSystemAdministrator() {
        UUID userId = insertUserWithRole("branch_inactive_admin", "SYSTEM_ADMIN");
        UUID branchId = insertBranch("INACTIVE");
        jdbcTemplate.update(
            "UPDATE organization.branches SET status = 'INACTIVE' WHERE branch_id = ?",
            branchId
        );

        assertThrows(
            BranchAccessDeniedException.class,
            () -> branchAccessPort.requireAccess(userId, branchId)
        );
    }

    private UUID insertUserWithRole(String username, String roleCode) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO iam.users (
                    user_id, username, password_hash, display_name, status
                ) VALUES (?, ?, 'not-used', ?, 'ACTIVE')
                """,
            userId,
            username,
            "Branch access " + username
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

    private UUID insertBranch(String suffix) {
        UUID branchId = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO organization.branches (branch_id, code, name)
                VALUES (?, ?, ?)
                """,
            branchId,
            "BR-" + suffix + '-' + branchId.toString().substring(0, 8),
            "Sucursal " + suffix
        );
        return branchId;
    }

    private void assignBranch(UUID userId, UUID branchId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO iam.user_branch_access (user_id, branch_id, status)
                VALUES (?, ?, ?)
                """,
            userId,
            branchId,
            status
        );
    }
}
