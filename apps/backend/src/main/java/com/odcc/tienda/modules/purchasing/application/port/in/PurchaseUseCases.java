package com.odcc.tienda.modules.purchasing.application.port.in;

import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;

import java.util.List;
import java.util.UUID;

public interface PurchaseUseCases {
    Purchase create(CreatePurchaseCommand command);

    Purchase getById(UUID purchaseId);

    List<Purchase> list(ListPurchasesQuery query);

    Purchase confirm(UUID purchaseId);

    InventoryReceipt receive(ReceivePurchaseCommand command);
}
