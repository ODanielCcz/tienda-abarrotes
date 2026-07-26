package com.odcc.tienda.modules.catalog.application.command;

import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;

import java.util.UUID;

public record ChangeProductStatusCommand(UUID productId, ProductStatus status) {
}
