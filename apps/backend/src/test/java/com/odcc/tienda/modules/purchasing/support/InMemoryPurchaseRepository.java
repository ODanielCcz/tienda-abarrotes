package com.odcc.tienda.modules.purchasing.support;

import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemMismatchException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.port.out.PurchaseRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryPurchaseRepository implements PurchaseRepositoryPort {

    private static final UUID DEFAULT_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final Map<UUID, Purchase> purchases = new LinkedHashMap<>();
    private final Map<UUID, PurchaseItem> items = new LinkedHashMap<>();
    private boolean inventoryReceiptExists;

    @Override
    public Optional<Purchase> findByIdempotencyKey(UUID idempotencyKey) {
        return purchases.values().stream().filter(p -> idempotencyKey != null && idempotencyKey.equals(p.idempotencyKey())).findFirst();
    }

    @Override
    public boolean inventoryReceiptExists(UUID idempotencyKey) {
        return inventoryReceiptExists;
    }

    public void markInventoryReceiptExists() {
        inventoryReceiptExists = true;
    }

    @Override
    public Purchase create(CreatePurchaseCommand command) {
        UUID purchaseId = UUID.randomUUID();
        List<PurchaseItem> createdItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreatePurchaseItemCommand commandItem : command.items()) {
            BigDecimal lineTotal = commandItem.quantity().multiply(commandItem.unitCost());
            PurchaseItem item = new PurchaseItem(UUID.randomUUID(), purchaseId, commandItem.productPresentationId(), null, "Producto", "SKU", commandItem.quantity(), BigDecimal.ZERO, commandItem.unitCost(), BigDecimal.ZERO, BigDecimal.ZERO, lineTotal);
            items.put(item.purchaseItemId(), item);
            createdItems.add(item);
            total = total.add(lineTotal);
        }
        Purchase purchase = new Purchase(purchaseId, UUID.randomUUID(), command.warehouseId(), command.supplierId(), command.supplierDocument(), "DRAFT", "PENDING", "MXN", total, BigDecimal.ZERO, BigDecimal.ZERO, total, command.idempotencyKey(), Instant.now(), null, Instant.now(), createdItems);
        purchases.put(purchaseId, purchase);
        return purchase;
    }

    @Override
    public Optional<Purchase> findById(UUID purchaseId) {
        Purchase purchase = purchases.get(purchaseId);
        if (purchase == null) return Optional.empty();
        return Optional.of(withFreshItems(purchase));
    }

    @Override
    public List<Purchase> findAll(ListPurchasesQuery query) {
        return purchases.values().stream().map(this::withFreshItems).toList();
    }

    @Override
    public Purchase confirm(UUID purchaseId) {
        Purchase current = withFreshItems(purchases.get(purchaseId));
        Purchase confirmed = new Purchase(current.purchaseId(), current.branchId(), current.warehouseId(), current.supplierId(), current.supplierDocument(), "CONFIRMED", current.paymentStatus(), current.currencyCode(), current.subtotal(), current.discountTotal(), current.taxTotal(), current.total(), current.idempotencyKey(), current.purchasedAt(), Instant.now(), current.createdAt(), current.items());
        purchases.put(purchaseId, confirmed);
        return confirmed;
    }

    @Override
    public PurchaseItem findItemById(UUID purchaseId, UUID purchaseItemId) {
        PurchaseItem item = items.get(purchaseItemId);
        if (item == null) throw new PurchaseItemNotFoundException(purchaseItemId);
        if (!purchaseId.equals(item.purchaseId())) throw new PurchaseItemMismatchException(purchaseId, purchaseItemId);
        return item;
    }

    @Override
    public void addReceivedQuantity(UUID purchaseId, UUID purchaseItemId, BigDecimal quantity) {
        PurchaseItem current = findItemById(purchaseId, purchaseItemId);
        items.put(purchaseItemId, new PurchaseItem(current.purchaseItemId(), current.purchaseId(), current.productPresentationId(), current.lotId(), current.productNameSnapshot(), current.skuSnapshot(), current.quantity(), current.receivedQuantity().add(quantity), current.unitCost(), current.discountAmount(), current.taxAmount(), current.lineTotal()));
    }

    @Override
    public Purchase refreshStatusAfterReceive(UUID purchaseId) {
        Purchase current = withFreshItems(purchases.get(purchaseId));
        boolean complete = current.items().stream().allMatch(item -> item.receivedQuantity().compareTo(item.quantity()) >= 0);
        Purchase updated = new Purchase(current.purchaseId(), current.branchId(), current.warehouseId(), current.supplierId(), current.supplierDocument(), complete ? "RECEIVED" : "PARTIALLY_RECEIVED", current.paymentStatus(), current.currencyCode(), current.subtotal(), current.discountTotal(), current.taxTotal(), current.total(), current.idempotencyKey(), current.purchasedAt(), current.confirmedAt(), current.createdAt(), current.items());
        purchases.put(purchaseId, updated);
        return updated;
    }

    @Override
    public UUID findBranchIdByWarehouseId(UUID warehouseId) {
        return DEFAULT_BRANCH_ID;
    }

    private Purchase withFreshItems(Purchase purchase) {
        List<PurchaseItem> freshItems = items.values().stream().filter(item -> item.purchaseId().equals(purchase.purchaseId())).toList();
        return new Purchase(purchase.purchaseId(), purchase.branchId(), purchase.warehouseId(), purchase.supplierId(), purchase.supplierDocument(), purchase.status(), purchase.paymentStatus(), purchase.currencyCode(), purchase.subtotal(), purchase.discountTotal(), purchase.taxTotal(), purchase.total(), purchase.idempotencyKey(), purchase.purchasedAt(), purchase.confirmedAt(), purchase.createdAt(), freshItems);
    }
}
