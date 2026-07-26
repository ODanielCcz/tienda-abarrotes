package com.odcc.tienda.modules.purchasing.application.port.out;

import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepositoryPort {
    Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey);

    boolean inventoryReceiptExists(UUID idempotencyKey);

    Purchase create(CreatePurchaseCommand command);

    Optional<Purchase> findById(UUID purchaseId);

    List<Purchase> findAll(ListPurchasesQuery query);

    Purchase confirm(UUID purchaseId);

    PurchaseItem findItemById(UUID purchaseItemId);

    void addReceivedQuantity(UUID purchaseItemId, BigDecimal quantity);

    Purchase refreshStatusAfterReceive(UUID purchaseId);
}

