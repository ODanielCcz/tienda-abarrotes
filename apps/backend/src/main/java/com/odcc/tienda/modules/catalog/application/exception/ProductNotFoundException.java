package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("No existe un producto con id " + productId);
    }
}
