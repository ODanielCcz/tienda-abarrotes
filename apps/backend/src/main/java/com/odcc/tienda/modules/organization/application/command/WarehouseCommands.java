package com.odcc.tienda.modules.organization.application.command;

import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseType;

import java.util.UUID;

public final class WarehouseCommands {
    private WarehouseCommands() {
    }

    public record CreateWarehouseCommand(UUID branchId, String code, String name, WarehouseType warehouseType) {
    }

    public record UpdateWarehouseCommand(UUID warehouseId, UUID branchId, String code, String name, WarehouseType warehouseType) {
    }

    public record ChangeWarehouseStatusCommand(UUID warehouseId, WarehouseStatus status) {
    }
}