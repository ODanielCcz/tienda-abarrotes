package com.odcc.tienda.modules.identity.application.usecase;

import com.odcc.tienda.modules.identity.application.command.AssignRolePermissionsCommand;
import com.odcc.tienda.modules.identity.application.command.AssignUserBranchesCommand;
import com.odcc.tienda.modules.identity.application.command.AssignUserRolesCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeRoleStatusCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserPasswordCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserStatusCommand;
import com.odcc.tienda.modules.identity.application.command.CreateRoleCommand;
import com.odcc.tienda.modules.identity.application.command.CreateUserCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateRoleCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateUserCommand;
import com.odcc.tienda.modules.identity.application.exception.BranchNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.IdentityException;
import com.odcc.tienda.modules.identity.application.exception.LastSystemAdminException;
import com.odcc.tienda.modules.identity.application.exception.PermissionNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.RoleCodeAlreadyExistsException;
import com.odcc.tienda.modules.identity.application.exception.RoleNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.SelfDisableNotAllowedException;
import com.odcc.tienda.modules.identity.application.exception.SystemRoleProtectedException;
import com.odcc.tienda.modules.identity.application.exception.UserAlreadyExistsException;
import com.odcc.tienda.modules.identity.application.exception.UserManagementNotFoundException;
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleDetail;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.port.in.UserManagementUseCases;
import com.odcc.tienda.modules.identity.application.port.out.PasswordHashingPort;
import com.odcc.tienda.modules.identity.application.port.out.PasswordPolicyPort;
import com.odcc.tienda.modules.identity.application.port.out.UserManagementRepositoryPort;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;
import com.odcc.tienda.modules.identity.domain.model.RoleStatus;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessDeniedException;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class UserManagementService implements UserManagementUseCases {

    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final int USERNAME_MAX_LENGTH = 80;
    private static final int DISPLAY_NAME_MAX_LENGTH = 200;
    private static final int ROLE_CODE_MAX_LENGTH = 80;
    private static final int ROLE_NAME_MAX_LENGTH = 150;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final UserManagementRepositoryPort repository;
    private final PasswordHashingPort passwordHashingPort;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final BranchAccessPort branchAccess;
    private final PasswordPolicyPort passwordPolicyPort;

    @Override
    public ManagedUser create(CreateUserCommand command) {
        return transactionRunner.required(() -> {
            String username = normalizeUsername(command == null ? null : command.username());
            String displayName = normalizeRequired(command == null ? null : command.displayName(), "El nombre visible es obligatorio", DISPLAY_NAME_MAX_LENGTH);
            String password = requirePassword(
                command == null ? null : command.password()
            );
            passwordPolicyPort.validate(username, password);
            Set<String> roleCodes = normalizeRoleCodes(command == null ? null : command.roleCodes());
            validateRolesExist(roleCodes);
            requireGlobalAccess(command.actorUserId());

            if (repository.existsByUsername(username)) {
                throw new UserAlreadyExistsException(username);
            }

            ManagedUser created = repository.create(UUID.randomUUID(), username, displayName, passwordHashingPort.hash(password), UserAccountStatus.ACTIVE);
            repository.replaceRoles(created.userId(), roleCodes);
            ManagedUser saved = getById(created.userId());

            auditPort.record(new BusinessAuditEvent("USER_CREATED", "USER", saved.userId(), Map.of(), state(saved), Map.of()));
            return saved;
        });
    }

    @Override
    public ManagedUser getById(UUID userId) {
        if (userId == null) throw new IdentityException("El usuario es obligatorio");
        return repository.findById(userId).orElseThrow(() -> new UserManagementNotFoundException(userId));
    }

    @Override
    public List<ManagedUser> list(ListUsersQuery query) {
        return repository.findAll(query);
    }

    @Override
    public ManagedUser update(UpdateUserCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null) throw new IdentityException("El usuario es obligatorio");
            ManagedUser current = getById(command.userId());
            requireActorCanMutateTarget(command.actorUserId(), current);
            String username = normalizeUsername(command.username());
            String displayName = normalizeRequired(command.displayName(), "El nombre visible es obligatorio", DISPLAY_NAME_MAX_LENGTH);

            if (repository.existsByUsernameAndIdNot(username, command.userId())) throw new UserAlreadyExistsException(username);

            ManagedUser updated = repository.update(command.userId(), username, displayName);
            auditPort.record(new BusinessAuditEvent("USER_UPDATED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public ManagedUser changeStatus(ChangeUserStatusCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null || command.status() == null) throw new IdentityException("El usuario y estado son obligatorios");
            repository.lockSystemAdminMutations();
            ManagedUser current = getById(command.userId());
            requireActorCanMutateTarget(command.currentUserId(), current);
            if (command.userId().equals(command.currentUserId()) && command.status() != UserAccountStatus.ACTIVE) throw new SelfDisableNotAllowedException();
            if (repository.hasSystemAdminRole(command.userId()) && command.status() != UserAccountStatus.ACTIVE && repository.countActiveSystemAdminsExcluding(command.userId()) == 0) throw new LastSystemAdminException();

            ManagedUser updated = repository.updateStatus(command.userId(), command.status());
            auditPort.record(new BusinessAuditEvent("USER_STATUS_CHANGED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public ManagedUser changePassword(ChangeUserPasswordCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null) throw new IdentityException("El usuario es obligatorio");
            ManagedUser current = getById(command.userId());
            requireActorCanMutateTarget(command.actorUserId(), current);
            String password = requirePassword(command.password());
            passwordPolicyPort.validate(current.username(), password);
            ManagedUser updated = repository.updatePassword(command.userId(), passwordHashingPort.hash(password));
            auditPort.record(new BusinessAuditEvent("USER_PASSWORD_CHANGED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public ManagedUser assignRoles(AssignUserRolesCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null) throw new IdentityException("El usuario es obligatorio");
            repository.lockSystemAdminMutations();
            ManagedUser current = getById(command.userId());
            Set<String> roleCodes = normalizeRoleCodes(command.roleCodes());
            validateRolesExist(roleCodes);

            BranchScope actorScope = requireActorCanMutateTarget(command.actorUserId(), current);
            if (current.roles().equals(roleCodes)) return current;

            requireAssignableRoles(command.actorUserId(), roleCodes, actorScope);
            if (command.userId().equals(command.actorUserId()) && !current.roles().containsAll(roleCodes)) {
                throw new BranchAccessDeniedException();
            }

            if (current.roles().contains(SYSTEM_ADMIN_ROLE) && !roleCodes.contains(SYSTEM_ADMIN_ROLE) && repository.countActiveSystemAdminsExcluding(command.userId()) == 0) throw new LastSystemAdminException();

            repository.replaceRoles(command.userId(), roleCodes);
            ManagedUser updated = getById(command.userId());
            auditPort.record(new BusinessAuditEvent("USER_ROLES_UPDATED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public ManagedUser assignBranches(AssignUserBranchesCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null) throw new IdentityException("El usuario es obligatorio");
            ManagedUser current = getById(command.userId());
            Set<UUID> branchIds = normalizeBranchIds(command.branchIds());
            validateBranchesExistAndActive(branchIds);

            BranchScope actorScope = resolveActorScope(command.actorUserId());
            if (!actorScope.globalAccess()) {
                boolean expandsTarget = !current.branchIds().containsAll(branchIds);
                boolean controlsCurrentScope = actorScope.branchIds().containsAll(current.branchIds());
                if (expandsTarget || !controlsCurrentScope || current.roles().contains(SYSTEM_ADMIN_ROLE)) {
                    throw new BranchAccessDeniedException();
                }
            }

            repository.replaceUserBranches(command.userId(), branchIds);
            ManagedUser updated = getById(command.userId());
            auditPort.record(new BusinessAuditEvent("USER_BRANCHES_UPDATED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public RoleDetail createRole(CreateRoleCommand command) {
        return transactionRunner.required(() -> {
            String code = normalizeRoleCode(command == null ? null : command.code());
            String name = normalizeRequired(command == null ? null : command.name(), "El nombre del rol es obligatorio", ROLE_NAME_MAX_LENGTH);
            String description = normalizeOptional(command == null ? null : command.description(), DESCRIPTION_MAX_LENGTH, "La descripcion del rol");

            if (repository.existsRoleCode(code)) throw new RoleCodeAlreadyExistsException(code);

            RoleDetail created = repository.createRole(UUID.randomUUID(), code, name, description, RoleStatus.ACTIVE);
            auditPort.record(new BusinessAuditEvent("ROLE_CREATED", "ROLE", created.roleId(), Map.of(), roleState(created), Map.of()));
            return created;
        });
    }

    @Override
    public RoleDetail updateRole(UpdateRoleCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.roleId() == null) throw new IdentityException("El rol es obligatorio");
            RoleDetail current = getRole(command.roleId());
            String code = normalizeRoleCode(command.code());
            String name = normalizeRequired(command.name(), "El nombre del rol es obligatorio", ROLE_NAME_MAX_LENGTH);
            String description = normalizeOptional(command.description(), DESCRIPTION_MAX_LENGTH, "La descripcion del rol");

            if (current.system() && !current.code().equals(code)) throw new SystemRoleProtectedException("No se puede cambiar el codigo de un rol de sistema");
            if (repository.existsRoleCodeAndIdNot(code, command.roleId())) throw new RoleCodeAlreadyExistsException(code);

            RoleDetail updated = repository.updateRole(command.roleId(), code, name, description);
            auditPort.record(new BusinessAuditEvent("ROLE_UPDATED", "ROLE", updated.roleId(), roleState(current), roleState(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public RoleDetail changeRoleStatus(ChangeRoleStatusCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.roleId() == null || command.status() == null) throw new IdentityException("El rol y estado son obligatorios");
            RoleDetail current = getRole(command.roleId());
            if (SYSTEM_ADMIN_ROLE.equals(current.code()) && command.status() != RoleStatus.ACTIVE) throw new SystemRoleProtectedException("No se puede desactivar el rol SYSTEM_ADMIN");

            RoleDetail updated = repository.updateRoleStatus(command.roleId(), command.status());
            auditPort.record(new BusinessAuditEvent("ROLE_STATUS_CHANGED", "ROLE", updated.roleId(), roleState(current), roleState(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public RoleDetail assignRolePermissions(AssignRolePermissionsCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.roleId() == null) throw new IdentityException("El rol es obligatorio");
            requireGlobalAccess(command.actorUserId());
            RoleDetail current = getRole(command.roleId());
            if (SYSTEM_ADMIN_ROLE.equals(current.code())) throw new SystemRoleProtectedException("No se pueden modificar los permisos del rol SYSTEM_ADMIN desde este endpoint");
            Set<String> permissionCodes = normalizePermissionCodes(command.permissionCodes());
            validatePermissionsExist(permissionCodes);

            repository.replaceRolePermissions(command.roleId(), permissionCodes);
            RoleDetail updated = getRole(command.roleId());
            auditPort.record(new BusinessAuditEvent("ROLE_PERMISSIONS_UPDATED", "ROLE", updated.roleId(), roleState(current), roleState(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public List<RoleSummary> listRoles() {
        return repository.findRoles();
    }

    @Override
    public List<PermissionSummary> listPermissions() {
        return repository.findPermissions();
    }

    private RoleDetail getRole(UUID roleId) {
        return repository.findRoleById(roleId).orElseThrow(() -> new RoleNotFoundException(roleId.toString()));
    }

    private BranchScope resolveActorScope(UUID actorUserId) {
        if (actorUserId == null) throw new BranchAccessDeniedException();
        BranchScope scope = branchAccess.resolveScope(actorUserId);
        if (scope == null) throw new BranchAccessDeniedException();
        return scope;
    }

    private void requireGlobalAccess(UUID actorUserId) {
        if (!resolveActorScope(actorUserId).globalAccess()) throw new BranchAccessDeniedException();
    }

    private BranchScope requireActorCanMutateTarget(UUID actorUserId, ManagedUser target) {
        BranchScope actorScope = resolveActorScope(actorUserId);
        if (actorScope.globalAccess()) return actorScope;
        if (target.roles().contains(SYSTEM_ADMIN_ROLE)
            || target.branchIds().isEmpty()
            || !actorScope.branchIds().containsAll(target.branchIds())) {
            throw new BranchAccessDeniedException();
        }
        return actorScope;
    }

    private void requireAssignableRoles(UUID actorUserId, Set<String> roleCodes, BranchScope actorScope) {
        if (actorScope.globalAccess()) return;
        if (roleCodes.contains(SYSTEM_ADMIN_ROLE)) throw new BranchAccessDeniedException();

        ManagedUser actor = getById(actorUserId);
        Set<String> grantedPermissions = repository.findPermissionCodesForRoles(roleCodes);
        if (!actor.permissions().containsAll(grantedPermissions)) throw new BranchAccessDeniedException();
    }

    private void validateRolesExist(Set<String> roleCodes) {
        Set<String> activeRoles = repository.findActiveRoleCodes(roleCodes);
        for (String roleCode : roleCodes) {
            if (!activeRoles.contains(roleCode)) throw new RoleNotFoundException(roleCode);
        }
    }

    private void validatePermissionsExist(Set<String> permissionCodes) {
        Set<String> existingPermissions = repository.findExistingPermissionCodes(permissionCodes);
        for (String permissionCode : permissionCodes) {
            if (!existingPermissions.contains(permissionCode)) throw new PermissionNotFoundException(permissionCode);
        }
    }

    private void validateBranchesExistAndActive(Set<UUID> branchIds) {
        Set<UUID> activeBranches = repository.findActiveBranchIds(branchIds);
        for (UUID branchId : branchIds) {
            if (!activeBranches.contains(branchId)) throw new BranchNotFoundException(branchId);
        }
    }

    private static Map<String, Object> state(ManagedUser user) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("username", user.username());
        state.put("displayName", user.displayName());
        state.put("status", user.status().name());
        state.put("roles", user.roles());
        state.put("permissions", user.permissions());
        state.put("branchIds", user.branchIds());
        return state;
    }

    private static Map<String, Object> roleState(RoleDetail role) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", role.code());
        state.put("name", role.name());
        state.put("description", role.description());
        state.put("system", role.system());
        state.put("status", role.status());
        state.put("permissions", role.permissions());
        return state;
    }

    private static String normalizeUsername(String username) {
        String normalized = normalizeRequired(username, "El username es obligatorio", USERNAME_MAX_LENGTH).toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) throw new IdentityException("El username solo acepta letras, numeros, punto, guion y guion bajo");
        return normalized;
    }

    private static String normalizeRoleCode(String roleCode) {
        String normalized = normalizeRequired(roleCode, "El codigo del rol es obligatorio", ROLE_CODE_MAX_LENGTH).toUpperCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(normalized).matches()) throw new IdentityException("El codigo del rol solo acepta letras mayusculas, numeros y guion bajo");
        return normalized;
    }

    private static Set<String> normalizeRoleCodes(Set<String> roleCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roleCodes == null) return Set.of();
        for (String roleCode : roleCodes) {
            String value = normalize(roleCode);
            if (value != null) normalized.add(value.toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static Set<String> normalizePermissionCodes(Set<String> permissionCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (permissionCodes == null) return Set.of();
        for (String permissionCode : permissionCodes) {
            String value = normalize(permissionCode);
            if (value != null) normalized.add(value.toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static Set<UUID> normalizeBranchIds(Set<UUID> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) return Set.of();
        return Set.copyOf(new LinkedHashSet<>(branchIds));
    }

    private static String normalizeRequired(String value, String message, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) throw new IdentityException(message);
        if (normalized.length() > maxLength) throw new IdentityException(message + ", maximo " + maxLength + " caracteres");
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String fieldName) {
        String normalized = normalize(value);
        if (normalized != null && normalized.length() > maxLength) throw new IdentityException(fieldName + " debe tener maximo " + maxLength + " caracteres");
        return normalized;
    }

    private static String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IdentityException("La contraseña es obligatoria");
        }
        if (password.length() > 255) {
            throw new IdentityException(
                "La contraseña es obligatoria, maximo 255 caracteres"
            );
        }
        return password;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
