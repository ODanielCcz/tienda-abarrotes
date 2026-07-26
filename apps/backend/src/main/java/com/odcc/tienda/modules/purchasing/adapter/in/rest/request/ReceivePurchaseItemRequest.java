package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivePurchaseItemRequest(
    @NotNull UUID purchaseItemId,
    String lotNumber,
    LocalDate manufacturedAt,
    LocalDate expiresAt,
    @NotNull @DecimalMin("0.001") BigDecimal quantity
) {
}
