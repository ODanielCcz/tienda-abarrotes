package com.odcc.tienda.modules.catalog.application.exception;

public class CategoryCodeAlreadyExistsException extends RuntimeException {

    public CategoryCodeAlreadyExistsException(String code) {
        super("Ya existe una categoria con el codigo " + code);
    }
}