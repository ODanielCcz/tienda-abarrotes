package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AssignUserRolesRequest(
    @NotNull Set<String> roleCodes
) {
}
