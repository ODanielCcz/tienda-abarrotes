package com.odcc.tienda.modules.inventory.application.usecase;

import com.odcc.tienda.modules.inventory.application.exception.InventoryReceiptNotFoundException;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.port.in.GetInventoryReceiptByIdUseCase;
import com.odcc.tienda.modules.inventory.application.port.out.InventoryReceiptRepositoryPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class GetInventoryReceiptByIdService implements GetInventoryReceiptByIdUseCase {

    private final InventoryReceiptRepositoryPort repository;
    private final BranchAccessPort branchAccessPort;

    @Override
    public InventoryReceipt execute(UUID receiptId) {
        return repository.findById(receiptId).orElseThrow(() -> new InventoryReceiptNotFoundException(receiptId));
    }

    @Override
    public InventoryReceipt execute(UUID receiptId, UUID actorUserId) {
        InventoryReceipt receipt = execute(receiptId);
        branchAccessPort.requireAccess(actorUserId, repository.findBranchIdByWarehouseId(receipt.warehouseId()));
        return receipt;
    }
}
