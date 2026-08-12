package com.odcc.tienda.modules.identity.application.command;

import java.util.UUID;

public record UpdateUserCommand(
    UUID actorUserId,
    UUID userId,
    String username,
    String displayName
) {

    public UpdateUserCommand(UUID userId, String username, String displayName) {
        this(null, userId, username, displayName);
    }
}
