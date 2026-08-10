package com.odcc.tienda.modules.identity.application.command;

public record LoginCommand(
    String username,
    String password,
    String clientAddress
) {
    public LoginCommand(String username, String password) {
        this(username, password, "unknown");
    }
}
