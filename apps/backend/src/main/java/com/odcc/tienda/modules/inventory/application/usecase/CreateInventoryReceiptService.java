package com.odcc.tienda.modules.inventory.application.usecase;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptAlreadyExistsException;
import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptException;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptPallet;
import com.odcc.tienda.modules.inventory.application.port.in.CreateInventoryReceiptUseCase;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptRepositoryPort;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateInventoryReceiptService implements CreateInventoryReceiptUseCase {

    private final InventoryReceiptRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public InventoryReceipt execute(CreateInventoryReceiptCommand command) {
        validate(command);
        String fingerprint = fingerprint(command);
        return transactionRunner.required(() -> create(command, fingerprint));
    }

    private InventoryReceipt create(CreateInventoryReceiptCommand command, String fingerprint) {
        if (command.idempotencyKey() != null) {
            if (repository.existsByIdempotencyKeyWithDifferentFingerprint(command.idempotencyKey(), fingerprint)) {
                throw new InventoryReceiptAlreadyExistsException(command.idempotencyKey());
            }
            var existing = repository.findByIdempotencyKey(command.idempotencyKey(), fingerprint);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        InventoryReceipt receipt = repository.create(command, fingerprint);
        auditPort.record(new BusinessAuditEvent(
            "INVENTORY_RECEIPT_CREATED",
            "INVENTORY_RECEIPT",
            receipt.receiptId(),
            Map.of(),
            receiptState(receipt),
            Map.of("warehouseId", receipt.warehouseId())
        ));
        for (InventoryReceiptPallet pallet : receipt.pallets()) {
            auditPort.record(new BusinessAuditEvent(
                "PALLET_RECEIVED",
                "PALLET",
                pallet.palletId(),
                Map.of(),
                Map.of(
                    "palletCode", pallet.palletCode(),
                    "externalPalletCode", pallet.externalPalletCode() == null ? "" : pallet.externalPalletCode(),
                    "items", pallet.items().size()
                ),
                Map.of("receiptId", receipt.receiptId())
            ));
        }
        return receipt;
    }

    private void validate(CreateInventoryReceiptCommand command) {
        if (command == null) {
            throw new InventoryReceiptException("La recepcion es obligatoria");
        }
        if (command.warehouseId() == null) {
            throw new InventoryReceiptException("El almacen es obligatorio");
        }
        int simpleItems = command.items() == null ? 0 : command.items().size();
        int palletItems = command.pallets() == null ? 0 : command.pallets().stream()
            .mapToInt(pallet -> pallet.items() == null ? 0 : pallet.items().size())
            .sum();
        if (simpleItems + palletItems == 0) {
            throw new InventoryReceiptException("La recepcion debe incluir al menos un item");
        }
    }

    private static Map<String, Object> receiptState(InventoryReceipt receipt) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("warehouseId", receipt.warehouseId());
        if (receipt.supplierId() != null) state.put("supplierId", receipt.supplierId());
        state.put("status", receipt.status());
        state.put("items", receipt.items().size());
        state.put("pallets", receipt.pallets().size());
        return state;
    }

    private static String fingerprint(CreateInventoryReceiptCommand command) {
        StringBuilder builder = new StringBuilder();
        builder.append(command.warehouseId()).append('|')
            .append(command.supplierId()).append('|')
            .append(normalize(command.reason())).append('|');
        if (command.items() != null) {
            command.items().stream()
                .map(CreateInventoryReceiptService::itemFingerprint)
                .sorted()
                .forEach(value -> builder.append("I:").append(value).append('|'));
        }
        if (command.pallets() != null) {
            command.pallets().stream()
                .map(CreateInventoryReceiptService::palletFingerprint)
                .sorted()
                .forEach(value -> builder.append("P:").append(value).append('|'));
        }
        return sha256(builder.toString());
    }

    private static String palletFingerprint(InventoryReceiptPalletCommand pallet) {
        StringBuilder builder = new StringBuilder();
        builder.append(normalize(pallet.externalPalletCode())).append(':');
        if (pallet.items() != null) {
            pallet.items().stream()
                .map(CreateInventoryReceiptService::itemFingerprint)
                .sorted()
                .forEach(value -> builder.append(value).append(','));
        }
        return builder.toString();
    }

    private static String itemFingerprint(InventoryReceiptItemCommand item) {
        return Objects.toString(item.productPresentationId(), "") + ':'
            + normalize(item.lotNumber()) + ':'
            + Objects.toString(item.manufacturedAt(), "") + ':'
            + Objects.toString(item.expiresAt(), "") + ':'
            + normalizeNumber(item.quantity()) + ':'
            + normalizeNumber(item.unitCost());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String normalizeNumber(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}

