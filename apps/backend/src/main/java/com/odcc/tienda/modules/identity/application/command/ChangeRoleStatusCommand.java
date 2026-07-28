package com.odcc.tienda.modules.identity.application.command;

import com.odcc.tienda.modules.identity.domain.model.RoleStatus;

import java.util.UUID;

public record ChangeRoleStatusCommand(
    UUID roleId,
    RoleStatus status
) {
}