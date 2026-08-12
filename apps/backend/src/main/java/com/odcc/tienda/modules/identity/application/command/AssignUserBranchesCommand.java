package com.odcc.tienda.modules.identity.application.command;

import java.util.Set;
import java.util.UUID;

public record AssignUserBranchesCommand(
    UUID actorUserId,
    UUID userId,
    Set<UUID> branchIds
) {

    public AssignUserBranchesCommand(UUID userId, Set<UUID> branchIds) {
        this(null, userId, branchIds);
    }
}
