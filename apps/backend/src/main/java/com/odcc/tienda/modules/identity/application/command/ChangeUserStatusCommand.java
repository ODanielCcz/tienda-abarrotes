package com.odcc.tienda.modules.identity.application.command;

import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;

import java.util.UUID;

public record ChangeUserStatusCommand(
    UUID currentUserId,
    UUID userId,
    UserAccountStatus status
) {
}
