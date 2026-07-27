package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import com.odcc.tienda.modules.identity.domain.model.UserAccountStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeUserStatusRequest(
    @NotNull UserAccountStatus status
) {
}
