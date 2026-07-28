package com.odcc.tienda.modules.organization.application.model;

import com.odcc.tienda.modules.organization.domain.model.DeviceStatus;
import com.odcc.tienda.modules.organization.domain.model.DeviceType;

import java.time.Instant;
import java.util.UUID;

public record DeviceView(
    UUID deviceId,
    UUID branchId,
    UUID warehouseId,
    String deviceCode,
    DeviceType deviceType,
    String platform,
    String appVersion,
    DeviceStatus status,
    Instant lastSeenAt,
    Instant createdAt,
    Instant updatedAt
) {
}