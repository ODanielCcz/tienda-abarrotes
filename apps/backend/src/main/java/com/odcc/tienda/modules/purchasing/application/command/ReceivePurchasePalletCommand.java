package com.odcc.tienda.modules.purchasing.application.command;

import java.util.List;

public record ReceivePurchasePalletCommand(
    String externalPalletCode,
    List<ReceivePurchaseItemCommand> items
) {
}
