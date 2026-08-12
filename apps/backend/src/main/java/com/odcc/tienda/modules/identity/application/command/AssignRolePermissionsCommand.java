package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

public record AssignRolePermissionsCommand(
    UUID actorUserId,
    UUID roleId,
    Set<String> permissionCodes
) {

    public AssignRolePermissionsCommand(UUID roleId, Set<String> permissionCodes) {
        this(null, roleId, permissionCodes);
    }
}
