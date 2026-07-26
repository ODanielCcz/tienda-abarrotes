package com.odcc.tienda.modules.identity.application.command;

public record LoginCommand(
    String username,
    String password
) {
}
