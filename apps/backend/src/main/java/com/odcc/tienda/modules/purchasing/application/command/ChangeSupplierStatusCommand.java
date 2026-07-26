package com.odcc.tienda.modules.purchasing.application.command;

import java.util.UUID;

public record ChangeSupplierStatusCommand(UUID supplierId, String status) {
}
