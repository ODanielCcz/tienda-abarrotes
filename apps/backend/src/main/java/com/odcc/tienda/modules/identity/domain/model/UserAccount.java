package com.odcc.tienda.modules.identity.domain.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

public record UserAccount(
    UUID id,
    String username,
    String passwordHash,
    String displayName,
    UserAccountStatus status,
    Set<String> roles,
    Set<String> permissions,
    int failedLoginAttempts,
    Instant lockedUntil,
    long authVersion
) {

    public UserAccount(
        UUID id,
        String username,
        String passwordHash,
        String displayName,
        UserAccountStatus status,
        Set<String> roles,
        Set<String> permissions
    ) {
        this(id, username, passwordHash, displayName, status, roles, permissions, 0, null, 0);
    }

    public UserAccount {
        Objects.requireNonNull(id, "El id del usuario es obligatorio");
        Objects.requireNonNull(username, "El nombre de usuario es obligatorio");
        Objects.requireNonNull(passwordHash, "El hash de contraseña es obligatorio");
        Objects.requireNonNull(displayName, "El nombre visible es obligatorio");
        Objects.requireNonNull(status, "El estado del usuario es obligatorio");
        if (failedLoginAttempts < 0) throw new IllegalArgumentException("Los intentos fallidos no pueden ser negativos");
        if (authVersion < 0) throw new IllegalArgumentException("La version de autenticacion no puede ser negativa");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public UserAccount withAuthenticationState(int attempts, Instant lockUntil) {
        return new UserAccount(
            id, username, passwordHash, displayName, status, roles, permissions,
            attempts, lockUntil, authVersion
        );
    }

    public Set<String> authorities() {
        Set<String> authorities = new LinkedHashSet<>(permissions);
        roles.forEach(role -> authorities.add("ROLE_" + role));
        return Set.copyOf(authorities);
    }
}
