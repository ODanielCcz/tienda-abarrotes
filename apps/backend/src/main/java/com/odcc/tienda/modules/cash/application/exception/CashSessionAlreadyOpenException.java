package com.odcc.tienda.modules.cash.application.exception;

import java.util.UUID;

public class CashSessionAlreadyOpenException extends CashException {
    public CashSessionAlreadyOpenException(UUID cashRegisterId) {
        super("Ya existe una sesion abierta para la caja " + cashRegisterId);
    }
}
