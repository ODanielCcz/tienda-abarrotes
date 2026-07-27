package com.odcc.tienda.modules.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record RoleSummary(
    UUID roleId,
    String code,
    String name,
    String description,
    boolean system,
    String status,
    Instant createdAt
) {
}
