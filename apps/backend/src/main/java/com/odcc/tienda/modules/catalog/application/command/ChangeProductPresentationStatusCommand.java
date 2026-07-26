package com.odcc.tienda.modules.catalog.application.command;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentationStatus;

import java.util.UUID;

public record ChangeProductPresentationStatusCommand(UUID presentationId, ProductPresentationStatus status) {
}
