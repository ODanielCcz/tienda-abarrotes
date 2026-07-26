package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeCategoryStatusRequest(
    @NotNull(message = "El estado de la categoria es obligatorio")
    CategoryStatus status
) {
}