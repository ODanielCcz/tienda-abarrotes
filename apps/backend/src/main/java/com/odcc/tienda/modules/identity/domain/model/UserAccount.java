package com.odcc.tienda.modules.identity.domain.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record UserAccount(
    UUID id,
    String username,
    String passwordHash,
    String displayName,
    UserAccountStatus status,
    Set<String> roles,
    Set<String> permissions
) {

    public UserAccount {
        Objects.requireNonNull(id, "El id del usuario es obligatorio");
        Objects.requireNonNull(username, "El nombre de usuario es obligatorio");
        Objects.requireNonNull(passwordHash, "El hash de contraseña es obligatorio");
        Objects.requireNonNull(displayName, "El nombre visible es obligatorio");
        Objects.requireNonNull(status, "El estado del usuario es obligatorio");
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public Set<String> authorities() {
        Set<String> authorities = new LinkedHashSet<>(permissions);
        roles.forEach(role -> authorities.add("ROLE_" + role));
        return Set.copyOf(authorities);
    }
}
