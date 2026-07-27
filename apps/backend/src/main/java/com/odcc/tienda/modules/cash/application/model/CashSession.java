package com.odcc.tienda.modules.cash.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSession(
    UUID cashSessionId,
    UUID cashRegisterId,
    UUID branchId,
    String cashRegisterCode,
    String cashRegisterName,
    UUID openedBy,
    UUID closedBy,
    String status,
    BigDecimal openingAmount,
    BigDecimal expectedAmount,
    BigDecimal countedAmount,
    BigDecimal differenceAmount,
    Instant openedAt,
    Instant closedAt,
    String notes
) {
}
