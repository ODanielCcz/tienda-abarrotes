package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

public record CreateUserCommand(
    UUID actorUserId,
    String username,
    String displayName,
    String password,
    Set<String> roleCodes
) {

    public CreateUserCommand(String username, String displayName, String password, Set<String> roleCodes) {
        this(null, username, displayName, password, roleCodes);
    }
}
