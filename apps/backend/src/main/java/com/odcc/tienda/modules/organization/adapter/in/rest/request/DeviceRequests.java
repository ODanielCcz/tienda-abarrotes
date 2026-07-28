package com.odcc.tienda.modules.organization.adapter.in.rest.request;

import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class DeviceRequests {
    private DeviceRequests() {
    }

    public record CreateDeviceRequest(@NotNull UUID branchId, UUID warehouseId, @NotBlank @Size(max = 80) String deviceCode, @NotNull DeviceType deviceType, @Size(max = 50) String platform, @Size(max = 50) String appVersion) {
    }

    public record UpdateDeviceRequest(@NotNull UUID branchId, UUID warehouseId, @NotBlank @Size(max = 80) String deviceCode, @NotNull DeviceType deviceType, @Size(max = 50) String platform, @Size(max = 50) String appVersion) {
    }

    public record ChangeDeviceStatusRequest(@NotNull DeviceStatus status) {
    }
}