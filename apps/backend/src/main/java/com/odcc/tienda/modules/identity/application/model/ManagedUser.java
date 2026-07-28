package com.odcc.tienda.modules.identity.application.model;

import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ManagedUser(
    UUID userId,
    String username,
    String displayName,
    UserAccountStatus status,
    Set<String> roles,
    Set<String> permissions,
    Set<UUID> branchIds,
    Instant createdAt,
    Instant updatedAt
) {
}