package com.odcc.tienda.modules.sales.application.exception;

import java.math.BigDecimal;
import java.util.UUID;

public final class SalesPriceChangedException extends RuntimeException {

    public SalesPriceChangedException(UUID presentationId, BigDecimal expected, BigDecimal current) {
        super("El precio de la presentacion " + presentationId
            + " cambio de " + expected + " a " + current);
    }
}
