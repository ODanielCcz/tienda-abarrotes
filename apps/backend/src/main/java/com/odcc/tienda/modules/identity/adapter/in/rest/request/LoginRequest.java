package com.odcc.tienda.modules.identity.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 80, message = "El nombre de usuario no puede superar 80 caracteres")
    String username,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 200, message = "La contraseña no puede superar 200 caracteres")
    String password
) {
}
