package com.odcc.tienda.modules.inventory.application.port.in;

import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptExecution;
import java.util.UUID;

public interface CreateInventoryReceiptUseCase {
    InventoryReceipt execute(CreateInventoryReceiptCommand command);

    default InventoryReceipt execute(CreateInventoryReceiptCommand command, UUID actorUserId) {
        return execute(command);
    }

    default InventoryReceiptExecution executeWithOutcome(CreateInventoryReceiptCommand command) {
        return InventoryReceiptExecution.created(execute(command));
    }

    default InventoryReceiptExecution executeWithOutcome(CreateInventoryReceiptCommand command, UUID actorUserId) {
        return executeWithOutcome(command);
    }
}
