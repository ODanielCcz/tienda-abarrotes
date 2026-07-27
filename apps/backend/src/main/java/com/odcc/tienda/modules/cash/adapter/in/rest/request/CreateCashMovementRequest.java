package com.odcc.tienda.modules.cash.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCashMovementRequest(
    @NotBlank String movementType,
    @NotBlank String direction,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @Size(max = 200) String reference,
    @Size(max = 1000) String reason
) {
}