package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class CategoryParentNotFoundException extends RuntimeException {

    public CategoryParentNotFoundException(UUID parentCategoryId) {
        super("No existe una categoria padre con id " + parentCategoryId);
    }
}