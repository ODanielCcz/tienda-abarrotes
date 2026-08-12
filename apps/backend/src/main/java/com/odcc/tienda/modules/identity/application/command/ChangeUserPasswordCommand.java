package com.odcc.tienda.modules.identity.application.command;

import java.util.UUID;

public record ChangeUserPasswordCommand(
    UUID actorUserId,
    UUID userId,
    String password
) {

    public ChangeUserPasswordCommand(UUID userId, String password) {
        this(null, userId, password);
    }
}
