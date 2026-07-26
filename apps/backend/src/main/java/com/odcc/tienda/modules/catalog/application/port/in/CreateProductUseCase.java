package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.command.CreateProductCommand;
import com.odcc.tienda.modules.catalog.domain.model.Product;

public interface CreateProductUseCase {
    Product execute(CreateProductCommand command);
}
