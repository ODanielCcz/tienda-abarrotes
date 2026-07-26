package com.odcc.tienda.modules.catalog.application.exception;

public final class BrandCodeAlreadyExistsException extends RuntimeException{

    public BrandCodeAlreadyExistsException(String code) {
        super("Ya existe una marca con el código: " + code);
    }
}
