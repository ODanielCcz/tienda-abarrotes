package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

public record AssignRolePermissionsCommand(
    UUID roleId,
    Set<String> permissionCodes
) {
}