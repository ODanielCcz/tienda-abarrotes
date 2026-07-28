package com.odcc.tienda.modules.identity.application.command;

import java.util.UUID;

public record UpdateRoleCommand(
    UUID roleId,
    String code,
    String name,
    String description
) {
}