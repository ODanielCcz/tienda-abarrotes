package com.odcc.tienda.modules.sales.application.command;

import java.util.UUID;

public record ConfirmSalesReturnCommand(
    UUID returnId,
    UUID cashSessionId,
    UUID confirmedBy
) {
}