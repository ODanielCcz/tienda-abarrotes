package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.ChangeProductPresentationStatusCommand;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

public interface ChangeProductPresentationStatusUseCase {
    ProductPresentation execute(ChangeProductPresentationStatusCommand command);
}
