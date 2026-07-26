package com.odcc.tienda.modules.catalog.application.command;

import java.util.UUID;

public record UpdateBrandCommand(
    UUID brandId,
    String code,
    String name
) {
}
