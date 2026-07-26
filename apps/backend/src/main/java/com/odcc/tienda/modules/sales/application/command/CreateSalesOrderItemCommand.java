package com.odcc.tienda.modules.sales.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesOrderItemCommand(
    UUID productPresentationId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountAmount
) {
}
