package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
    @NotBlank @Size(max = 80) String code,
    @NotBlank @Size(max = 150) String name,
    @Size(max = 500) String description
) {
}