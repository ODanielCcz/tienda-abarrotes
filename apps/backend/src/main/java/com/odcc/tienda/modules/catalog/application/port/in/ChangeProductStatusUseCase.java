package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.ChangeProductStatusCommand;
import com.odcc.tienda.modules.catalog.domain.model.Product;

public interface ChangeProductStatusUseCase {
    Product execute(ChangeProductStatusCommand command);
}
