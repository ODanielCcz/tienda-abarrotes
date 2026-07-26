package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.UpdateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

public interface UpdateProductPresentationUseCase {
    ProductPresentation execute(UpdateProductPresentationCommand command);
}
