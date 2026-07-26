package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.UpdateProductCommand;
import com.odcc.tienda.modules.catalog.domain.model.Product;

public interface UpdateProductUseCase {
    Product execute(UpdateProductCommand command);
}
