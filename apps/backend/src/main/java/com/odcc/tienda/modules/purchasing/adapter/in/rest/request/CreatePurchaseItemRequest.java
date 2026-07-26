package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseItemRequest(
    @NotNull UUID productPresentationId,
    @NotNull @DecimalMin("0.001") BigDecimal quantity,
    @NotNull @DecimalMin("0.0000") BigDecimal unitCost,
    @DecimalMin("0.0000") BigDecimal discountAmount,
    @DecimalMin("0.0000") BigDecimal taxAmount
) {
}
