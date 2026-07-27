package com.odcc.tienda.modules.identity.application.usecase;

import com.odcc.tienda.modules.identity.application.command.AssignUserRolesCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserPasswordCommand;
import com.odcc.tienda.modules.identity.application.command.ChangeUserStatusCommand;
import com.odcc.tienda.modules.identity.application.command.CreateUserCommand;
import com.odcc.tienda.modules.identity.application.command.UpdateUserCommand;
import com.odcc.tienda.modules.identity.application.exception.IdentityException;
import com.odcc.tienda.modules.identity.application.exception.LastSystemAdminException;
import com.odcc.tienda.modules.identity.application.exception.RoleNotFoundException;
import com.odcc.tienda.modules.identity.application.exception.SelfDisableNotAllowedException;
import com.odcc.tienda.modules.identity.application.exception.UserAlreadyExistsException;
import com.odcc.tienda.modules.identity.application.exception.UserManagementNotFoundException;
import com.odcc.tienda.modules.identity.application.model.ManagedUser;
import com.odcc.tienda.modules.identity.application.model.PermissionSummary;
import com.odcc.tienda.modules.identity.application.model.RoleSummary;
import com.odcc.tienda.modules.identity.application.port.in.UserManagementUseCases;
import com.odcc.tienda.modules.identity.application.port.out.PasswordHashingPort;
import com.odcc.tienda.modules.identity.application.port.out.UserManagementRepositoryPort;
import com.odcc.tienda.modules.identity.application.query.ListUsersQuery;
import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
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

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]+$");
    private static final int USERNAME_MAX_LENGTH = 80;
    private static final int DISPLAY_NAME_MAX_LENGTH = 200;

    private final UserManagementRepositoryPort repository;
    private final PasswordHashingPort passwordHashingPort;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public ManagedUser create(CreateUserCommand command) {
        return transactionRunner.required(() -> {
            String username = normalizeUsername(command == null ? null : command.username());
            String displayName = normalizeRequired(command == null ? null : command.displayName(), "El nombre visible es obligatorio", DISPLAY_NAME_MAX_LENGTH);
            String password = normalizeRequired(command == null ? null : command.password(), "La contraseña es obligatoria", 255);
            Set<String> roleCodes = normalizeRoleCodes(command == null ? null : command.roleCodes());
            validateRolesExist(roleCodes);

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
            ManagedUser current = getById(command.userId());
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
            String password = normalizeRequired(command.password(), "La contraseña es obligatoria", 255);
            ManagedUser updated = repository.updatePassword(command.userId(), passwordHashingPort.hash(password));
            auditPort.record(new BusinessAuditEvent("USER_PASSWORD_CHANGED", "USER", updated.userId(), state(current), state(updated), Map.of()));
            return updated;
        });
    }

    @Override
    public ManagedUser assignRoles(AssignUserRolesCommand command) {
        return transactionRunner.required(() -> {
            if (command == null || command.userId() == null) throw new IdentityException("El usuario es obligatorio");
            ManagedUser current = getById(command.userId());
            Set<String> roleCodes = normalizeRoleCodes(command.roleCodes());
            validateRolesExist(roleCodes);

            if (current.roles().contains("SYSTEM_ADMIN") && !roleCodes.contains("SYSTEM_ADMIN") && repository.countActiveSystemAdminsExcluding(command.userId()) == 0) throw new LastSystemAdminException();

            repository.replaceRoles(command.userId(), roleCodes);
            ManagedUser updated = getById(command.userId());
            auditPort.record(new BusinessAuditEvent("USER_ROLES_UPDATED", "USER", updated.userId(), state(current), state(updated), Map.of()));
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

    private void validateRolesExist(Set<String> roleCodes) {
        Set<String> activeRoles = repository.findActiveRoleCodes(roleCodes);
        for (String roleCode : roleCodes) {
            if (!activeRoles.contains(roleCode)) throw new RoleNotFoundException(roleCode);
        }
    }

    private static Map<String, Object> state(ManagedUser user) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("username", user.username());
        state.put("displayName", user.displayName());
        state.put("status", user.status().name());
        state.put("roles", user.roles());
        return state;
    }

    private static String normalizeUsername(String username) {
        String normalized = normalizeRequired(username, "El username es obligatorio", USERNAME_MAX_LENGTH).toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) throw new IdentityException("El username solo acepta letras, numeros, punto, guion y guion bajo");
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

    private static String normalizeRequired(String value, String message, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) throw new IdentityException(message);
        if (normalized.length() > maxLength) throw new IdentityException(message + ", maximo " + maxLength + " caracteres");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
