package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class ProductBrandNotFoundException extends RuntimeException {

    public ProductBrandNotFoundException(UUID brandId) {
        super("No existe una marca para el producto con id " + brandId);
    }
}
