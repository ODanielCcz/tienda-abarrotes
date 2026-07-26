package com.odcc.tienda.modules.purchasing.application.command;

import java.util.List;
import java.util.UUID;

public record ReceivePurchaseCommand(
    UUID purchaseId,
    UUID idempotencyKey,
    List<ReceivePurchaseItemCommand> items,
    List<ReceivePurchasePalletCommand> pallets
) {
}
