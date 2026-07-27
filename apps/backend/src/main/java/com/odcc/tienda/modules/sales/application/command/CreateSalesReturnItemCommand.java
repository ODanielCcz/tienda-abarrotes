package com.odcc.tienda.modules.sales.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesReturnItemCommand(
    UUID salesOrderItemId,
    BigDecimal quantity
) {
}