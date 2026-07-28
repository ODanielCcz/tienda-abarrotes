package com.odcc.tienda.modules.organization.application.model;

import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseType;

import java.time.Instant;
import java.util.UUID;

public record WarehouseView(
    UUID warehouseId,
    UUID branchId,
    String code,
    String name,
    WarehouseType warehouseType,
    WarehouseStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}