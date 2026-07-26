package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public class StockInsufficientException extends RuntimeException {
    public StockInsufficientException(UUID productPresentationId) {
        super("Stock insuficiente para la presentacion " + productPresentationId);
    }
}
