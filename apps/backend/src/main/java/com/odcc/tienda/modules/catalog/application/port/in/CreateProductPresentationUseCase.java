package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.CreateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

public interface CreateProductPresentationUseCase {
    ProductPresentation execute(CreateProductPresentationCommand command);
}
