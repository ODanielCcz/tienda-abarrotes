package com.odcc.tienda.modules.sales.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesPaymentCommand(
    UUID salesOrderId,
    UUID cashSessionId,
    String paymentMethod,
    BigDecimal amount,
    String currencyCode,
    String reference,
    UUID idempotencyKey,
    UUID createdBy
) {
}
