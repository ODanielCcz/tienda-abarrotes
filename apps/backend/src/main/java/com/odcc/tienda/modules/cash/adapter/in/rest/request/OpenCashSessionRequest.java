package com.odcc.tienda.modules.cash.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenCashSessionRequest(
    @NotNull UUID cashRegisterId,
    @NotNull @DecimalMin("0.00") BigDecimal openingAmount,
    String notes
) {
}
