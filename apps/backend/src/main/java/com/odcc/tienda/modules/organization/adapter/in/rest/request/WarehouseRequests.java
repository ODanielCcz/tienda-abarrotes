package com.odcc.tienda.modules.organization.adapter.in.rest.request;

import com.odcc.tienda.modules.organization.domain.model.WarehouseStatus;
import com.odcc.tienda.modules.organization.domain.model.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class WarehouseRequests {
    private WarehouseRequests() {
    }

    public record CreateWarehouseRequest(@NotNull UUID branchId, @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name, WarehouseType warehouseType) {
    }

    public record UpdateWarehouseRequest(@NotNull UUID branchId, @NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name, WarehouseType warehouseType) {
    }

    public record ChangeWarehouseStatusRequest(@NotNull WarehouseStatus status) {
    }
}