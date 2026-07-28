package com.odcc.tienda.modules.organization.application.model;

import com.odcc.tienda.modules.organization.domain.model.BranchStatus;

import java.time.Instant;
import java.util.UUID;

public record BranchView(
    UUID branchId,
    String code,
    String name,
    String legalName,
    String timezone,
    String currencyCode,
    BranchStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}