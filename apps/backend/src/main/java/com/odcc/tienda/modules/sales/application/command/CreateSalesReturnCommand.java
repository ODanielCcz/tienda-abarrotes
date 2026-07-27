package com.odcc.tienda.modules.sales.application.command;

import java.util.List;
import java.util.UUID;

public record CreateSalesReturnCommand(
    UUID salesOrderId,
    String reason,
    UUID createdBy,
    List<CreateSalesReturnItemCommand> items
) {
}