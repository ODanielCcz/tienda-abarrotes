package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchasePalletCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.port.in.PurchaseUseCases;
import com.odcc.tienda.modules.purchasing.application.port.out.PurchaseRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class PurchaseService implements PurchaseUseCases {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PurchaseRepositoryPort repository;
    private final CreateInventoryReceiptUseCase inventoryReceiptUseCase;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Purchase create(CreatePurchaseCommand command) {
        return transactionRunner.required(() -> {
            validateCreate(command);
            if (command.idempotencyKey() != null) {
                var existing = repository.findByIdempotencyKey(command.idempotencyKey());
                if (existing.isPresent()) return existing.get();
            }
            Purchase purchase = repository.create(command);
            auditPort.record(new BusinessAuditEvent("PURCHASE_CREATED", "PURCHASE", purchase.purchaseId(), Map.of(), Map.of("status", purchase.status(), "total", purchase.total()), Map.of()));
            return purchase;
        });
    }

    @Override
    public Purchase getById(UUID purchaseId) {
        return repository.findById(purchaseId).orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
    }

    @Override
    public List<Purchase> list(ListPurchasesQuery query) {
        return repository.findAll(query);
    }

    @Override
    public Purchase confirm(UUID purchaseId) {
        return transactionRunner.required(() -> {
            Purchase current = getById(purchaseId);
            if (!"DRAFT".equals(current.status())) throw new PurchasingException("Solo se pueden confirmar compras en estado DRAFT");
            if (current.items() == null || current.items().isEmpty()) throw new PurchasingException("No se puede confirmar una compra sin items");
            Purchase confirmed = repository.confirm(purchaseId);
            auditPort.record(new BusinessAuditEvent("PURCHASE_CONFIRMED", "PURCHASE", confirmed.purchaseId(), Map.of("status", current.status()), Map.of("status", confirmed.status(), "total", confirmed.total()), Map.of()));
            return confirmed;
        });
    }

    @Override
    public InventoryReceipt receive(ReceivePurchaseCommand command) {
        return transactionRunner.required(() -> {
            Purchase purchase = getById(command.purchaseId());
            if (!List.of("CONFIRMED", "PARTIALLY_RECEIVED").contains(purchase.status())) {
                throw new PurchasingException("Solo se pueden recibir compras CONFIRMED o PARTIALLY_RECEIVED");
            }
            validateReceive(command);
            boolean alreadyApplied = command.idempotencyKey() != null && repository.inventoryReceiptExists(command.idempotencyKey());
            List<InventoryReceiptItemCommand> items = command.items() == null ? List.of() : command.items().stream().map(this::toInventoryItem).toList();
            List<InventoryReceiptPalletCommand> pallets = command.pallets() == null ? List.of() : command.pallets().stream().map(this::toInventoryPallet).toList();
            InventoryReceipt receipt = inventoryReceiptUseCase.execute(new CreateInventoryReceiptCommand(
                purchase.warehouseId(),
                purchase.supplierId(),
                command.idempotencyKey(),
                "Recepcion de compra " + purchase.purchaseId(),
                items,
                pallets
            ));
            if (!alreadyApplied) {
                applyReceivedQuantities(command);
                Purchase updated = repository.refreshStatusAfterReceive(purchase.purchaseId());
                auditPort.record(new BusinessAuditEvent("PURCHASE_RECEIVED", "PURCHASE", updated.purchaseId(), Map.of("status", purchase.status()), Map.of("status", updated.status()), Map.of("inventoryReceiptId", receipt.receiptId())));
            }
            return receipt;
        });
    }

    private void validateCreate(CreatePurchaseCommand command) {
        if (command == null) throw new PurchasingException("La compra es obligatoria");
        if (command.warehouseId() == null) throw new PurchasingException("El almacen es obligatorio");
        if (command.supplierId() == null) throw new PurchasingException("El proveedor es obligatorio");
        if (command.items() == null || command.items().isEmpty()) throw new PurchasingException("La compra debe incluir items");
        command.items().forEach(item -> {
            if (item.productPresentationId() == null) throw new PurchasingException("La presentacion es obligatoria");
            if (item.quantity() == null || item.quantity().compareTo(ZERO) <= 0) throw new PurchasingException("La cantidad debe ser mayor a cero");
            if (item.unitCost() == null || item.unitCost().compareTo(ZERO) < 0) throw new PurchasingException("El costo unitario no puede ser negativo");
            if (item.discountAmount() != null && item.discountAmount().compareTo(ZERO) < 0) throw new PurchasingException("El descuento no puede ser negativo");
            if (item.taxAmount() != null && item.taxAmount().compareTo(ZERO) < 0) throw new PurchasingException("El impuesto no puede ser negativo");
        });
    }

    private void validateReceive(ReceivePurchaseCommand command) {
        int simpleItems = command.items() == null ? 0 : command.items().size();
        int palletItems = command.pallets() == null ? 0 : command.pallets().stream().mapToInt(p -> p.items() == null ? 0 : p.items().size()).sum();
        if (simpleItems + palletItems == 0) throw new PurchasingException("La recepcion debe incluir items");
        if (command.items() != null) command.items().forEach(this::validateReceivableQuantity);
        if (command.pallets() != null) command.pallets().forEach(p -> {
            if (p.items() == null || p.items().isEmpty()) throw new PurchasingException("El pallet debe incluir items");
            p.items().forEach(this::validateReceivableQuantity);
        });
    }

    private void validateReceivableQuantity(ReceivePurchaseItemCommand command) {
        if (command.purchaseItemId() == null) throw new PurchaseItemNotFoundException(null);
        if (command.quantity() == null || command.quantity().compareTo(ZERO) <= 0) throw new PurchasingException("La cantidad recibida debe ser mayor a cero");
        PurchaseItem item = repository.findItemById(command.purchaseItemId());
        BigDecimal pending = item.quantity().subtract(item.receivedQuantity());
        if (command.quantity().compareTo(pending) > 0) throw new PurchasingException("No se puede recibir mas de lo comprado para el item " + command.purchaseItemId());
    }

    private InventoryReceiptItemCommand toInventoryItem(ReceivePurchaseItemCommand command) {
        PurchaseItem item = repository.findItemById(command.purchaseItemId());
        return new InventoryReceiptItemCommand(item.productPresentationId(), command.lotNumber(), command.manufacturedAt(), command.expiresAt(), command.quantity(), item.unitCost());
    }

    private InventoryReceiptPalletCommand toInventoryPallet(ReceivePurchasePalletCommand command) {
        return new InventoryReceiptPalletCommand(command.externalPalletCode(), command.items().stream().map(this::toInventoryItem).toList());
    }

    private void applyReceivedQuantities(ReceivePurchaseCommand command) {
        if (command.items() != null) command.items().forEach(item -> repository.addReceivedQuantity(item.purchaseItemId(), item.quantity()));
        if (command.pallets() != null) command.pallets().forEach(pallet -> pallet.items().forEach(item -> repository.addReceivedQuantity(item.purchaseItemId(), item.quantity())));
    }

    private boolean isNewReceiptForThisCall(InventoryReceipt receipt, List<InventoryReceiptItemCommand> items, List<InventoryReceiptPalletCommand> pallets) {
        int expected = items.size() + pallets.stream().mapToInt(p -> p.items().size()).sum();
        int actual = receipt.items().size() + receipt.pallets().stream().mapToInt(p -> p.items().size()).sum();
        return actual == expected;
    }
}

