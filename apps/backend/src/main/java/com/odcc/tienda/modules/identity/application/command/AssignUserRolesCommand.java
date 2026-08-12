package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

public record AssignUserRolesCommand(
    UUID actorUserId,
    UUID userId,
    Set<String> roleCodes
) {

    public AssignUserRolesCommand(UUID userId, Set<String> roleCodes) {
        this(null, userId, roleCodes);
    }
}
