package com.odcc.tienda.modules.catalog.domain.exception;

public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String message) {
        super(message);
    }
}