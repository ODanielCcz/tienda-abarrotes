package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class ProductCategoryNotFoundException extends RuntimeException {

    public ProductCategoryNotFoundException(UUID categoryId) {
        super("No existe una categoria para el producto con id " + categoryId);
    }
}
