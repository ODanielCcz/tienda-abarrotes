package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
    @NotBlank(message = "El codigo de la categoria es obligatorio")
    @Size(max = 50, message = "El codigo de la categoria no puede superar 50 caracteres")
    String code,

    @NotBlank(message = "El nombre de la categoria es obligatorio")
    @Size(max = 150, message = "El nombre de la categoria no puede superar 150 caracteres")
    String name,

    UUID parentCategoryId
) {
}