package com.odcc.tienda.modules.identity.application.command;

import java.util.UUID;

public record UpdateUserCommand(
    UUID userId,
    String username,
    String displayName
) {
}
