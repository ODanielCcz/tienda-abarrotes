package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("No existe una categoria con id " + categoryId);
    }
}