package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

import java.util.List;
import java.util.UUID;

public interface ListProductPresentationsUseCase {
    List<ProductPresentation> execute(UUID productId);
}
