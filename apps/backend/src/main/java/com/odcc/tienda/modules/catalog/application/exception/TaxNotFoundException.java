package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class TaxNotFoundException extends RuntimeException {
    public TaxNotFoundException(UUID taxId) {
        super("No existe un impuesto con id " + taxId);
    }
}
