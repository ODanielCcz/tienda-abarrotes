package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.Product;

import java.util.UUID;

public interface GetProductByIdUseCase {
    Product execute(UUID productId);
}
