package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import com.odcc.tienda.modules.catalog.domain.model.ProductType;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateProductRequest(
    UUID categoryId,
    UUID brandId,

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 200, message = "El nombre del producto no puede superar 200 caracteres")
    String name,

    @Size(max = 1000, message = "La descripcion del producto no puede superar 1000 caracteres")
    String description,

    ProductType productType,
    Boolean tracksInventory,
    Boolean tracksLots,
    Boolean tracksExpiration
) {
}
