package com.odcc.tienda.modules.catalog.adapter.in.rest.request;

import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeBrandStatusRequest(
    @NotNull(message = "El estado de la marca es obligatorio")
    BrandStatus status
) {
}
