package com.odcc.tienda.modules.identity.application.port.out;

import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserManagementRepositoryPort {

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID userId);

    ManagedUser create(UUID userId, String username, String displayName, String passwordHash, UserAccountStatus status);

    ManagedUser update(UUID userId, String username, String displayName);

    ManagedUser updateStatus(UUID userId, UserAccountStatus status);

    ManagedUser updatePassword(UUID userId, String passwordHash);

    Optional<ManagedUser> findById(UUID userId);

    List<ManagedUser> findAll(ListUsersQuery query);

    Set<String> findActiveRoleCodes(Set<String> roleCodes);

    void replaceRoles(UUID userId, Set<String> roleCodes);

    long countActiveSystemAdminsExcluding(UUID excludedUserId);

    boolean hasSystemAdminRole(UUID userId);

    List<RoleSummary> findRoles();

    List<PermissionSummary> findPermissions();
}
