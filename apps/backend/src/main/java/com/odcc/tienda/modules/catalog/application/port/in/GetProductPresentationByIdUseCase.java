package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

import java.util.UUID;

public interface GetProductPresentationByIdUseCase {
    ProductPresentation execute(UUID presentationId);
}
