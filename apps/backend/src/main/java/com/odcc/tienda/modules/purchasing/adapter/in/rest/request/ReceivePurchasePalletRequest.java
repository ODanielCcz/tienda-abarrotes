package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReceivePurchasePalletRequest(
    String externalPalletCode,
    @Valid @NotEmpty List<ReceivePurchaseItemRequest> items
) {
}
