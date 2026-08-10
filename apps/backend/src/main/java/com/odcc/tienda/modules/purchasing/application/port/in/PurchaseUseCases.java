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

    default Purchase create(CreatePurchaseCommand command, UUID actorUserId) { return create(command); }

    default Purchase getById(UUID purchaseId, UUID actorUserId) { return getById(purchaseId); }

    default List<Purchase> list(ListPurchasesQuery query, UUID actorUserId) { return list(query); }

    default Purchase confirm(UUID purchaseId, UUID actorUserId) { return confirm(purchaseId); }

    default InventoryReceipt receive(ReceivePurchaseCommand command, UUID actorUserId) { return receive(command); }
}
