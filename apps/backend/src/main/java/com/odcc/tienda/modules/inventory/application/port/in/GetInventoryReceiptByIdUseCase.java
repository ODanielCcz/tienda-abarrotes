package com.odcc.tienda.modules.inventory.application.port.in;

import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;

import java.util.UUID;

public interface GetInventoryReceiptByIdUseCase {
    InventoryReceipt execute(UUID receiptId);
}
