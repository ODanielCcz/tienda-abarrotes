package com.odcc.tienda.modules.identity.application.command;

public record CreateRoleCommand(
    String code,
    String name,
    String description
) {
}