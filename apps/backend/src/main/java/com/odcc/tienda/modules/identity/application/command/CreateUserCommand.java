package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;

public record CreateUserCommand(
    String username,
    String displayName,
    String password,
    Set<String> roleCodes
) {
}
