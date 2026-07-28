package com.odcc.tienda.modules.organization.application.model;

import com.odcc.tienda.modules.organization.domain.model.CashRegisterStatus;

import java.time.Instant;
import java.util.UUID;

public record CashRegisterView(
    UUID cashRegisterId,
    UUID branchId,
    UUID deviceId,
    String code,
    String name,
    CashRegisterStatus status,
    Instant createdAt
) {
}