package com.odcc.tienda.modules.identity.application.model;

import java.time.Instant;
import java.util.UUID;

public record PermissionSummary(
    UUID permissionId,
    String code,
    String name,
    String module,
    String description,
    Instant createdAt
) {
}
