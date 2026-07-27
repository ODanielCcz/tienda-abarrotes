package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashSummaryReport(
    UUID cashSessionId,
    UUID cashRegisterId,
    String cashRegisterCode,
    String status,
    BigDecimal openingAmount,
    BigDecimal expectedAmount,
    BigDecimal countedAmount,
    BigDecimal differenceAmount,
    BigDecimal cashIn,
    BigDecimal cashOut,
    Instant openedAt,
    Instant closedAt
) {
}
