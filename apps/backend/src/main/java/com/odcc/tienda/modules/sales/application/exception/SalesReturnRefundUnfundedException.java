package com.odcc.tienda.modules.sales.application.exception;

import java.math.BigDecimal;

public final class SalesReturnRefundUnfundedException extends SalesException {

    public SalesReturnRefundUnfundedException(BigDecimal remainingAmount) {
        super(
            "Los pagos capturados no cubren el reembolso; faltan "
                + remainingAmount
        );
    }
}
