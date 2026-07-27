package com.odcc.tienda.modules.identity.adapter.out.persistence;

import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.port.out.UserManagementRepositoryPort;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserManagementJdbcAdapter implements UserManagementRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM iam.users WHERE LOWER(username) = LOWER(:username)", new MapSqlParameterSource("username", username), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByUsernameAndIdNot(String username, UUID userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM iam.users WHERE LOWER(username) = LOWER(:username) AND user_id <> :userId", new MapSqlParameterSource().addValue("username", username).addValue("userId", userId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public ManagedUser create(UUID userId, String username, String displayName, String passwordHash, UserAccountStatus status) {
        jdbc.update("""
            INSERT INTO iam.users (user_id, username, password_hash, display_name, status, password_changed_at)
            VALUES (:userId, :username, :passwordHash, :displayName, :status, clock_timestamp())
            """, new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("username", username)
            .addValue("passwordHash", passwordHash)
            .addValue("displayName", displayName)
            .addValue("status", status.name()));
        return findById(userId).orElseThrow();
    }

    @Override
    public ManagedUser update(UUID userId, String username, String displayName) {
        jdbc.update("""
            UPDATE iam.users
            SET username = :username,
                display_name = :displayName,
                version = version + 1,
                updated_at = clock_timestamp()
            WHERE user_id = :userId
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("username", username).addValue("displayName", displayName));
        return findById(userId).orElseThrow();
    }

    @Override
    public ManagedUser updateStatus(UUID userId, UserAccountStatus status) {
        jdbc.update("""
            UPDATE iam.users
            SET status = :status,
                version = version + 1,
                updated_at = clock_timestamp()
            WHERE user_id = :userId
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("status", status.name()));
        return findById(userId).orElseThrow();
    }

    @Override
    public ManagedUser updatePassword(UUID userId, String passwordHash) {
        jdbc.update("""
            UPDATE iam.users
            SET password_hash = :passwordHash,
                password_changed_at = clock_timestamp(),
                version = version + 1,
                updated_at = clock_timestamp()
            WHERE user_id = :userId
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("passwordHash", passwordHash));
        return findById(userId).orElseThrow();
    }

    @Override
    public Optional<ManagedUser> findById(UUID userId) {
        try {
            ManagedUserRow row = jdbc.queryForObject("SELECT user_id, username, display_name, status, created_at, updated_at FROM iam.users WHERE user_id = :userId", new MapSqlParameterSource("userId", userId), this::mapUserRow);
            return Optional.of(toManagedUser(row));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ManagedUser> findAll(ListUsersQuery query) {
        return jdbc.query("""
            SELECT DISTINCT user_account.user_id, user_account.username, user_account.display_name, user_account.status, user_account.created_at, user_account.updated_at
            FROM iam.users user_account
            LEFT JOIN iam.user_roles user_role ON user_role.user_id = user_account.user_id
            LEFT JOIN iam.roles role ON role.role_id = user_role.role_id
            WHERE (:status IS NULL OR user_account.status = :status)
              AND (:roleCode IS NULL OR role.code = :roleCode)
              AND (:search IS NULL OR user_account.username ILIKE :searchLike OR user_account.display_name ILIKE :searchLike)
            ORDER BY user_account.username
            LIMIT 200
            """, new MapSqlParameterSource()
            .addValue("status", query == null || query.status() == null ? null : query.status().name())
            .addValue("roleCode", normalize(query == null ? null : query.roleCode()))
            .addValue("search", normalizeSearch(query == null ? null : query.search()))
            .addValue("searchLike", searchLike(query == null ? null : query.search())),
            (rs, rowNum) -> toManagedUser(mapUserRow(rs, rowNum)));
    }

    @Override
    public Set<String> findActiveRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) return Set.of();
        return new LinkedHashSet<>(jdbc.queryForList("SELECT code FROM iam.roles WHERE status = 'ACTIVE' AND code IN (:roleCodes)", new MapSqlParameterSource("roleCodes", roleCodes), String.class));
    }

    @Override
    public void replaceRoles(UUID userId, Set<String> roleCodes) {
        jdbc.update("DELETE FROM iam.user_roles WHERE user_id = :userId", new MapSqlParameterSource("userId", userId));
        if (roleCodes == null || roleCodes.isEmpty()) return;
        jdbc.update("""
            INSERT INTO iam.user_roles (user_id, role_id)
            SELECT :userId, role_id
            FROM iam.roles
            WHERE status = 'ACTIVE'
              AND code IN (:roleCodes)
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("roleCodes", roleCodes));
    }

    @Override
    public long countActiveSystemAdminsExcluding(UUID excludedUserId) {
        Long count = jdbc.queryForObject("""
            SELECT COUNT(DISTINCT user_account.user_id)
            FROM iam.users user_account
            JOIN iam.user_roles user_role ON user_role.user_id = user_account.user_id
            JOIN iam.roles role ON role.role_id = user_role.role_id
            WHERE user_account.status = 'ACTIVE'
              AND role.status = 'ACTIVE'
              AND role.code = 'SYSTEM_ADMIN'
              AND user_account.user_id <> :excludedUserId
            """, new MapSqlParameterSource("excludedUserId", excludedUserId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean hasSystemAdminRole(UUID userId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM iam.user_roles user_role
            JOIN iam.roles role ON role.role_id = user_role.role_id
            WHERE user_role.user_id = :userId
              AND role.code = 'SYSTEM_ADMIN'
            """, new MapSqlParameterSource("userId", userId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<RoleSummary> findRoles() {
        return jdbc.query("""
            SELECT role_id, code, name, description, is_system, status, created_at
            FROM iam.roles
            ORDER BY code
            """, this::mapRole);
    }

    @Override
    public List<PermissionSummary> findPermissions() {
        return jdbc.query("""
            SELECT permission_id, code, name, module, description, created_at
            FROM iam.permissions
            ORDER BY module, code
            """, this::mapPermission);
    }

    private ManagedUser toManagedUser(ManagedUserRow row) {
        return new ManagedUser(row.userId(), row.username(), row.displayName(), row.status(), findRoles(row.userId()), findPermissions(row.userId()), row.createdAt().toInstant(), row.updatedAt().toInstant());
    }

    private Set<String> findRoles(UUID userId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT DISTINCT role.code
            FROM iam.user_roles user_role
            JOIN iam.roles role ON role.role_id = user_role.role_id
            WHERE user_role.user_id = :userId
              AND role.status = 'ACTIVE'
            ORDER BY role.code
            """, new MapSqlParameterSource("userId", userId), String.class));
    }

    private Set<String> findPermissions(UUID userId) {
        return new LinkedHashSet<>(jdbc.queryForList("""
            SELECT DISTINCT permission.code
            FROM iam.user_roles user_role
            JOIN iam.roles role ON role.role_id = user_role.role_id
            JOIN iam.role_permissions role_permission ON role_permission.role_id = role.role_id
            JOIN iam.permissions permission ON permission.permission_id = role_permission.permission_id
            WHERE user_role.user_id = :userId
              AND role.status = 'ACTIVE'
            ORDER BY permission.code
            """, new MapSqlParameterSource("userId", userId), String.class));
    }

    private ManagedUserRow mapUserRow(ResultSet rs, int rowNum) throws SQLException {
        return new ManagedUserRow(rs.getObject("user_id", UUID.class), rs.getString("username"), rs.getString("display_name"), UserAccountStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at"), rs.getTimestamp("updated_at"));
    }

    private RoleSummary mapRole(ResultSet rs, int rowNum) throws SQLException {
        return new RoleSummary(rs.getObject("role_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getBoolean("is_system"), rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    }

    private PermissionSummary mapPermission(ResultSet rs, int rowNum) throws SQLException {
        return new PermissionSummary(rs.getObject("permission_id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("module"), rs.getString("description"), rs.getTimestamp("created_at").toInstant());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String searchLike(String value) {
        String normalized = normalizeSearch(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private record ManagedUserRow(UUID userId, String username, String displayName, UserAccountStatus status, Timestamp createdAt, Timestamp updatedAt) {
    }
}
