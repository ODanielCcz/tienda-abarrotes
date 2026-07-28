package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import com.odcc.tienda.modules.identity.domain.model.RoleStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleStatusRequest(
    @NotNull RoleStatus status
) {
}