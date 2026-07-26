package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseRequest(
    @NotNull UUID warehouseId,
    @NotNull UUID supplierId,
    @Size(max = 100) String supplierDocument,
    @Size(min = 3, max = 3) String currencyCode,
    UUID idempotencyKey,
    @Valid @NotEmpty List<CreatePurchaseItemRequest> items
) {
}
