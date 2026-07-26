package com.odcc.tienda.modules.catalog.application.exception;

import java.util.UUID;

public class UnitOfMeasureNotFoundException extends RuntimeException {
    public UnitOfMeasureNotFoundException(UUID unitId) {
        super("No existe una unidad de medida con id " + unitId);
    }
}
