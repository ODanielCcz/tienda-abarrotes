package com.odcc.tienda.modules.cash.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenCashSessionCommand(
    UUID cashRegisterId,
    UUID openedBy,
    BigDecimal openingAmount,
    String notes
) {
}
