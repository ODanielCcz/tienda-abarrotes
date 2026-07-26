package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class ProductPresentationNotFoundException extends RuntimeException {
    public ProductPresentationNotFoundException(UUID presentationId) {
        super("No existe una presentacion con id " + presentationId);
    }
}
