package com.odcc.tienda.modules.purchasing.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchasePalletCommand;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseItemNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseIdempotencyConflictException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchaseNotFoundException;
import com.odcc.tienda.modules.purchasing.application.exception.PurchasingException;
import com.odcc.tienda.modules.purchasing.application.model.Purchase;
import com.odcc.tienda.modules.purchasing.application.model.PurchaseItem;
import com.odcc.tienda.modules.purchasing.application.port.in.PurchaseUseCases;
import com.odcc.tienda.modules.purchasing.application.port.out.PurchaseRepositoryPort;
import com.odcc.tienda.modules.purchasing.application.query.ListPurchasesQuery;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import com.odcc.tienda.shared.application.authorization.BranchScope;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class PurchaseService implements PurchaseUseCases {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PurchaseRepositoryPort repository;
    private final CreateInventoryReceiptUseCase inventoryReceiptUseCase;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    private final BranchAccessPort branchAccessPort;

    @Override
    public Purchase create(CreatePurchaseCommand command, UUID actorUserId) {
        return createInternal(command, actorUserId);
    }

    @Override
    public Purchase getById(UUID purchaseId, UUID actorUserId) {
        Purchase purchase = getById(purchaseId);
        requirePurchaseAccess(actorUserId, purchase);
        return purchase;
    }

    @Override
    public List<Purchase> list(ListPurchasesQuery query, UUID actorUserId) {
        if (query != null && query.warehouseId() != null) {
            branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(query.warehouseId()));
        }
        BranchScope scope = branchAccessPort.resolveScope(actorUserId);
        List<Purchase> purchases = list(query);
        if (scope.globalAccess()) return purchases;
        return purchases.stream()
            .filter(purchase -> scope.branchIds().contains(repository.findBranchIdByWarehouseId(purchase.warehouseId())))
            .toList();
    }

    @Override
    public Purchase confirm(UUID purchaseId, UUID actorUserId) {
        requirePurchaseAccess(actorUserId, getById(purchaseId));
        return confirm(purchaseId);
    }

    @Override
    public InventoryReceipt receive(ReceivePurchaseCommand command, UUID actorUserId) {
        requirePurchaseAccess(actorUserId, getById(command.purchaseId()));
        return receiveInternal(command, actorUserId);
    }

    @Override
    public Purchase create(CreatePurchaseCommand command) {
        return createInternal(command, null);
    }

    private Purchase createInternal(CreatePurchaseCommand command, UUID actorUserId) {
        return transactionRunner.required(() -> {
            validateCreate(command);
            if (command.idempotencyKey() != null) {
                repository.lockIdempotencyKey(command.idempotencyKey());
                var existing = repository.findByIdempotencyKey(command.idempotencyKey());
                if (existing.isPresent()) {
                    return resolveIdempotentReplay(command, actorUserId, existing.get());
                }
            }
            if (actorUserId != null) {
                branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(command.warehouseId()));
            }
            Purchase purchase = repository.create(command);
            auditPort.record(new BusinessAuditEvent("PURCHASE_CREATED", "PURCHASE", purchase.purchaseId(), Map.of(), Map.of("status", purchase.status(), "total", purchase.total()), Map.of()));
            return purchase;
        });
    }

    private Purchase resolveIdempotentReplay(CreatePurchaseCommand command, UUID actorUserId, Purchase persisted) {
        if (actorUserId != null) branchAccessPort.requireAccess(actorUserId, persisted.branchId());
        if (!fingerprint(command).equals(fingerprint(persisted))) {
            throw new PurchaseIdempotencyConflictException();
        }
        return persisted;
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
        return receiveInternal(command, null);
    }

    private InventoryReceipt receiveInternal(ReceivePurchaseCommand command, UUID actorUserId) {
        return transactionRunner.required(() -> {
            Purchase purchase = getById(command.purchaseId());
            if (!List.of("CONFIRMED", "PARTIALLY_RECEIVED").contains(purchase.status())) {
                throw new PurchasingException("Solo se pueden recibir compras CONFIRMED o PARTIALLY_RECEIVED");
            }
            validateReceive(command);
            boolean alreadyApplied = command.idempotencyKey() != null && repository.inventoryReceiptExists(command.idempotencyKey());
            List<InventoryReceiptItemCommand> items = command.items() == null ? List.of() : command.items().stream().map(item -> toInventoryItem(command.purchaseId(), item)).toList();
            List<InventoryReceiptPalletCommand> pallets = command.pallets() == null ? List.of() : command.pallets().stream().map(pallet -> toInventoryPallet(command.purchaseId(), pallet)).toList();
            CreateInventoryReceiptCommand receiptCommand = new CreateInventoryReceiptCommand(
                purchase.warehouseId(),
                purchase.supplierId(),
                command.idempotencyKey(),
                "Recepcion de compra " + purchase.purchaseId(),
                items,
                pallets
            );
            InventoryReceipt receipt = actorUserId == null
                ? inventoryReceiptUseCase.execute(receiptCommand)
                : inventoryReceiptUseCase.execute(receiptCommand, actorUserId);
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
        if (command.items() != null) command.items().forEach(item -> validateReceivableQuantity(command.purchaseId(), item));
        if (command.pallets() != null) command.pallets().forEach(p -> {
            if (p.items() == null || p.items().isEmpty()) throw new PurchasingException("El pallet debe incluir items");
            p.items().forEach(item -> validateReceivableQuantity(command.purchaseId(), item));
        });
    }

    private void requirePurchaseAccess(UUID actorUserId, Purchase purchase) {
        branchAccessPort.requireAccess(actorUserId, purchase.branchId());
    }

    private void validateReceivableQuantity(UUID purchaseId, ReceivePurchaseItemCommand command) {
        if (command.purchaseItemId() == null) throw new PurchaseItemNotFoundException(null);
        if (command.quantity() == null || command.quantity().compareTo(ZERO) <= 0) throw new PurchasingException("La cantidad recibida debe ser mayor a cero");
        PurchaseItem item = findOwnedItem(purchaseId, command.purchaseItemId());
        BigDecimal pending = item.quantity().subtract(item.receivedQuantity());
        if (command.quantity().compareTo(pending) > 0) throw new PurchasingException("No se puede recibir mas de lo comprado para el item " + command.purchaseItemId());
    }

    private InventoryReceiptItemCommand toInventoryItem(UUID purchaseId, ReceivePurchaseItemCommand command) {
        PurchaseItem item = findOwnedItem(purchaseId, command.purchaseItemId());
        return new InventoryReceiptItemCommand(item.productPresentationId(), command.lotNumber(), command.manufacturedAt(), command.expiresAt(), command.quantity(), item.unitCost());
    }

    private InventoryReceiptPalletCommand toInventoryPallet(UUID purchaseId, ReceivePurchasePalletCommand command) {
        return new InventoryReceiptPalletCommand(command.externalPalletCode(), command.items().stream().map(item -> toInventoryItem(purchaseId, item)).toList());
    }

    private void applyReceivedQuantities(ReceivePurchaseCommand command) {
        if (command.items() != null) command.items().forEach(item -> repository.addReceivedQuantity(command.purchaseId(), item.purchaseItemId(), item.quantity()));
        if (command.pallets() != null) command.pallets().forEach(pallet -> pallet.items().forEach(item -> repository.addReceivedQuantity(command.purchaseId(), item.purchaseItemId(), item.quantity())));
    }

    private PurchaseItem findOwnedItem(UUID purchaseId, UUID purchaseItemId) {
        return repository.findItemById(purchaseId, purchaseItemId);
    }

    private boolean isNewReceiptForThisCall(InventoryReceipt receipt, List<InventoryReceiptItemCommand> items, List<InventoryReceiptPalletCommand> pallets) {
        int expected = items.size() + pallets.stream().mapToInt(p -> p.items().size()).sum();
        int actual = receipt.items().size() + receipt.pallets().stream().mapToInt(p -> p.items().size()).sum();
        return actual == expected;
    }

    private static PurchaseFingerprint fingerprint(CreatePurchaseCommand command) {
        return new PurchaseFingerprint(
            command.warehouseId(),
            command.supplierId(),
            normalizeDocument(command.supplierDocument()),
            normalizeCurrency(command.currencyCode()),
            command.items().stream().map(PurchaseService::itemFingerprint).sorted().toList()
        );
    }

    private static PurchaseFingerprint fingerprint(Purchase purchase) {
        return new PurchaseFingerprint(
            purchase.warehouseId(),
            purchase.supplierId(),
            normalizeDocument(purchase.supplierDocument()),
            normalizeCurrency(purchase.currencyCode()),
            purchase.items().stream().map(PurchaseService::itemFingerprint).sorted().toList()
        );
    }

    private static String itemFingerprint(CreatePurchaseItemCommand item) {
        return Objects.toString(item.productPresentationId(), "") + ':'
            + number(item.quantity()) + ':'
            + number(item.unitCost()) + ':'
            + number(item.discountAmount()) + ':'
            + number(item.taxAmount());
    }

    private static String itemFingerprint(PurchaseItem item) {
        return Objects.toString(item.productPresentationId(), "") + ':'
            + number(item.quantity()) + ':'
            + number(item.unitCost()) + ':'
            + number(item.discountAmount()) + ':'
            + number(item.taxAmount());
    }

    private static String normalizeDocument(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "MXN" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String number(BigDecimal value) {
        return (value == null ? ZERO : value).stripTrailingZeros().toPlainString();
    }

    private record PurchaseFingerprint(
        UUID warehouseId,
        UUID supplierId,
        String supplierDocument,
        String currencyCode,
        List<String> items
    ) {
    }
}

