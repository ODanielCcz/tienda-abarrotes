package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSalesPaymentRequest(
    UUID cashSessionId,
    @NotBlank String paymentMethod,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String currencyCode,
    String reference,
    @NotNull UUID idempotencyKey
) {
}
