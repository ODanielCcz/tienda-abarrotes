package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public final class BrandNotFoundException extends RuntimeException {

    public BrandNotFoundException(UUID brandId) {
        super("No existe una marca con el id: " + brandId);
    }
}
