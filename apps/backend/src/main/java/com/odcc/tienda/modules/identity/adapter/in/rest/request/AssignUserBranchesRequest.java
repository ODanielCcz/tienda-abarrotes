package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record AssignUserBranchesRequest(
    @NotNull Set<UUID> branchIds
) {
}