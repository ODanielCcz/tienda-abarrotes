package com.odcc.tienda.modules.identity.application.command;

import java.util.UUID;

public record ChangeUserPasswordCommand(
    UUID userId,
    String password
) {
}
