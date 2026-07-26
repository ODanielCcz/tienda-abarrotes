package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateBrandRequest(

    @NotBlank(message = "El código de la marca es obligatorio")
    @Size(
        max = 50,
        message = "El código de la marca no puede superar 50 caracteres"
    )
    @Pattern(
        regexp = "^[A-Za-z0-9_-]+$",
        message = "El código solo puede contener letras, números, guiones y guiones bajos"
    )
    String code,

    @NotBlank(message = "El nombre de la marca es obligatorio")
    @Size(
        max = 150,
        message = "El nombre de la marca no puede superar 150 caracteres"
    )
    String name
) {
}
