package com.odcc.tienda.modules.inventory.application.port.out;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReceiptRepositoryPort {
    Optional<InventoryReceipt> findByIdempotencyKey(UUID idempotencyKey, String fingerprint);

    boolean existsByIdempotencyKeyWithDifferentFingerprint(UUID idempotencyKey, String fingerprint);

    InventoryReceipt create(CreateInventoryReceiptCommand command, String fingerprint);

    Optional<InventoryReceipt> findById(UUID receiptId);

    UUID findBranchIdByWarehouseId(UUID warehouseId);
}
