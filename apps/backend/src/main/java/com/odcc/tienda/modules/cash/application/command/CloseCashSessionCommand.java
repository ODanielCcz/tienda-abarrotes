package com.odcc.tienda.modules.cash.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CloseCashSessionCommand(
    UUID cashSessionId,
    UUID closedBy,
    BigDecimal countedCashAmount,
    String notes
) {
}
