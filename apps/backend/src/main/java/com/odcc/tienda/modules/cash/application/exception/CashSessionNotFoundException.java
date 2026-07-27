package com.odcc.tienda.modules.cash.application.exception;

import java.util.UUID;

public class CashSessionNotFoundException extends CashException {
    public CashSessionNotFoundException(UUID cashSessionId) {
        super("No existe la sesion de caja " + cashSessionId);
    }
}
