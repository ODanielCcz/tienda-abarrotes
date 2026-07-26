package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeProductStatusRequest(
    @NotNull(message = "El estado del producto es obligatorio")
    ProductStatus status
) {
}
