package com.odcc.tienda.modules.cash.application.exception;

import java.util.UUID;

public final class CashSessionAlreadyClosedException extends CashException {

    public CashSessionAlreadyClosedException(UUID cashSessionId) {
        super("La sesion de caja " + cashSessionId + " ya esta cerrada");
    }
}
