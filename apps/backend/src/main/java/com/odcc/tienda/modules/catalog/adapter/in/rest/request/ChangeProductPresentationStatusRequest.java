package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentationStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeProductPresentationStatusRequest(
    @NotNull(message = "El estado es obligatorio") ProductPresentationStatus status
) {
}
