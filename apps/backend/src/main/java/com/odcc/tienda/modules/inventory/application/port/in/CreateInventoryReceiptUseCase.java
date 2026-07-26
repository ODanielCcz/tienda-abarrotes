package com.odcc.tienda.modules.inventory.application.port.in;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;

public interface CreateInventoryReceiptUseCase {
    InventoryReceipt execute(CreateInventoryReceiptCommand command);
}
