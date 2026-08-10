package com.odcc.tienda.modules.sales.application.exception;

import java.util.UUID;

public final class PriceNotConfiguredException extends RuntimeException {

    public PriceNotConfiguredException(UUID presentationId) {
        super("No existe un precio vigente para la presentacion " + presentationId);
    }
}
