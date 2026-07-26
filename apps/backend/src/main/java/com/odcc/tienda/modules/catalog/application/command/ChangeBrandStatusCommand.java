package com.odcc.tienda.modules.catalog.application.command;

import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;

import java.util.UUID;

public record ChangeBrandStatusCommand(
    UUID brandId,
    BrandStatus status
) {
}
