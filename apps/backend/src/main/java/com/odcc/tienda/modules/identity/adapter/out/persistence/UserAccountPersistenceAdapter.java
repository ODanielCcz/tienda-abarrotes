package com.odcc.tienda.modules.identity.adapter.out.persistence;

import com.odcc.tienda.modules.identity.application.port.out.UserAccountPort;
import com.odcc.tienda.modules.identity.domain.model.UserAccount;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserAccountPersistenceAdapter implements UserAccountPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        MapSqlParameterSource parameters = new MapSqlParameterSource(
            "username",
            username
        );

        List<UserRow> users = jdbcTemplate.query(
            """
                SELECT user_id, username, password_hash, display_name, status
                FROM iam.users
                WHERE LOWER(username) = LOWER(:username)
                """,
            parameters,
            (resultSet, rowNumber) -> toUserRow(resultSet)
        );

        if (users.isEmpty()) {
            return Optional.empty();
        }

        UserRow user = users.getFirst();

        return Optional.of(
            new UserAccount(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.displayName(),
                user.status(),
                findRoles(user.id()),
                findPermissions(user.id())
            )
        );
    }

    private Set<String> findRoles(UUID userId) {
        return new LinkedHashSet<>(
            jdbcTemplate.queryForList(
                """
                    SELECT DISTINCT role.code
                    FROM iam.user_roles user_role
                    JOIN iam.roles role ON role.role_id = user_role.role_id
                    WHERE user_role.user_id = :userId
                      AND role.status = 'ACTIVE'
                      AND (
                          user_role.valid_until IS NULL
                          OR user_role.valid_until > clock_timestamp()
                      )
                    ORDER BY role.code
                    """,
                new MapSqlParameterSource("userId", userId),
                String.class
            )
        );
    }

    private Set<String> findPermissions(UUID userId) {
        return new LinkedHashSet<>(
            jdbcTemplate.queryForList(
                """
                    SELECT DISTINCT permission.code
                    FROM iam.user_roles user_role
                    JOIN iam.roles role ON role.role_id = user_role.role_id
                    JOIN iam.role_permissions role_permission
                      ON role_permission.role_id = role.role_id
                    JOIN iam.permissions permission
                      ON permission.permission_id = role_permission.permission_id
                    WHERE user_role.user_id = :userId
                      AND role.status = 'ACTIVE'
                      AND (
                          user_role.valid_until IS NULL
                          OR user_role.valid_until > clock_timestamp()
                      )
                    ORDER BY permission.code
                    """,
                new MapSqlParameterSource("userId", userId),
                String.class
            )
        );
    }

    private static UserRow toUserRow(ResultSet resultSet) throws SQLException {
        return new UserRow(
            resultSet.getObject("user_id", UUID.class),
            resultSet.getString("username"),
            resultSet.getString("password_hash"),
            resultSet.getString("display_name"),
            UserAccountStatus.valueOf(resultSet.getString("status"))
        );
    }

    private record UserRow(
        UUID id,
        String username,
        String passwordHash,
        String displayName,
        UserAccountStatus status
    ) {
    }
}
