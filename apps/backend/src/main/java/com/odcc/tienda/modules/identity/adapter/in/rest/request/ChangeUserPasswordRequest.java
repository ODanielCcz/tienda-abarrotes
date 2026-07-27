package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeUserPasswordRequest(
    @NotBlank @Size(max = 255) String password
) {
}
