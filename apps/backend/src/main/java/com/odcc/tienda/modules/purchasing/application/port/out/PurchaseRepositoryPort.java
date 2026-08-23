package com.odcc.tienda.modules.purchasing.application.port.out;

import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
import com.odcc.tienda.shared.application.authorization.BranchScope;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepositoryPort {
    Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey);

    boolean inventoryReceiptExists(UUID idempotencyKey);

    Purchase create(CreatePurchaseCommand command);

    default void lockIdempotencyKey(UUID idempotencyKey) {
    }

    Optional<Purchase> findById(UUID purchaseId);

    List<Purchase> findAll(ListPurchasesQuery query);

    default List<Purchase> findAll(
        ListPurchasesQuery query,
        BranchScope scope
    ) {
        List<Purchase> purchases = findAll(query);
        if (scope == null || scope.globalAccess()) return purchases;
        return purchases.stream().filter(purchase -> scope.allows(purchase.branchId())).toList();
    }

    Purchase confirm(UUID purchaseId);

    PurchaseItem findItemById(UUID purchaseId, UUID purchaseItemId);

    void addReceivedQuantity(UUID purchaseId, UUID purchaseItemId, BigDecimal quantity);

    Purchase refreshStatusAfterReceive(UUID purchaseId);

    UUID findBranchIdByWarehouseId(UUID warehouseId);
}

