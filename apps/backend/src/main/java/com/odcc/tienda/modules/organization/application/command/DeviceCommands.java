package com.odcc.tienda.modules.organization.application.command;

import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceType;

import java.util.UUID;

public final class DeviceCommands {
    private DeviceCommands() {
    }

    public record CreateDeviceCommand(UUID branchId, UUID warehouseId, String deviceCode, DeviceType deviceType, String platform, String appVersion) {
    }

    public record UpdateDeviceCommand(UUID deviceId, UUID branchId, UUID warehouseId, String deviceCode, DeviceType deviceType, String platform, String appVersion) {
    }

    public record ChangeDeviceStatusCommand(UUID deviceId, DeviceStatus status) {
    }
}