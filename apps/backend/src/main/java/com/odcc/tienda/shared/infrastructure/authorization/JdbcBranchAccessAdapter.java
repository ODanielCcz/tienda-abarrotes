package com.odcc.tienda.shared.infrastructure.authorization;

import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcBranchAccessAdapter implements BranchAccessPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public BranchScope resolveScope(UUID userId) {
        if (userId == null) {
            return BranchScope.restricted(Set.of());
        }
        Boolean globalAccess = jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1
                    FROM iam.users user_account
                    JOIN iam.user_roles user_role ON user_role.user_id = user_account.user_id
                    JOIN iam.roles role ON role.role_id = user_role.role_id
                    WHERE user_account.user_id = ?
                      AND user_account.status = 'ACTIVE'
                      AND role.code = 'SYSTEM_ADMIN'
                      AND role.status = 'ACTIVE'
                      AND (user_role.valid_until IS NULL OR user_role.valid_until > clock_timestamp())
                )
                """,
            Boolean.class,
            userId
        );
        if (Boolean.TRUE.equals(globalAccess)) {
            return BranchScope.global();
        }

        Set<UUID> branchIds = new LinkedHashSet<>(jdbcTemplate.query(
            """
                SELECT access.branch_id
                FROM iam.user_branch_access access
                JOIN iam.users user_account ON user_account.user_id = access.user_id
                JOIN organization.branches branch ON branch.branch_id = access.branch_id
                WHERE access.user_id = ?
                  AND access.status = 'ACTIVE'
                  AND user_account.status = 'ACTIVE'
                  AND branch.status = 'ACTIVE'
                ORDER BY access.branch_id
                """,
            (resultSet, rowNumber) -> resultSet.getObject("branch_id", UUID.class),
            userId
        ));
        return BranchScope.restricted(branchIds);
    }

    @Override
    public void requireAccess(UUID userId, UUID branchId) {
        if (branchId == null || !branchIsActive(branchId) || !resolveScope(userId).allows(branchId)) {
            throw new BranchAccessDeniedException(branchId);
        }
    }

    private boolean branchIsActive(UUID branchId) {
        Boolean active = jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1
                    FROM organization.branches
                    WHERE branch_id = ? AND status = 'ACTIVE'
                )
                """,
            Boolean.class,
            branchId
        );
        return Boolean.TRUE.equals(active);
    }
}
