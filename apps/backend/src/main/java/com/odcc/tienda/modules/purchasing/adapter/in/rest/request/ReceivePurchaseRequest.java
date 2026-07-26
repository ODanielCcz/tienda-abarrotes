package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record ReceivePurchaseRequest(
    UUID idempotencyKey,
    @Valid List<ReceivePurchaseItemRequest> items,
    @Valid List<ReceivePurchasePalletRequest> pallets
) {
}
