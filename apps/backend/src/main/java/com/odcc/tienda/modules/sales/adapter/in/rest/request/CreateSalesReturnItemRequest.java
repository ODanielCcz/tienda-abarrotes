package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesReturnItemRequest(
    @NotNull UUID salesOrderItemId,
    @NotNull @DecimalMin(value = "0.001") BigDecimal quantity
) {
}