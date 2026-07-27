package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Size(max = 255) String password,
    @NotNull Set<String> roleCodes
) {
}
