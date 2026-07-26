package com.odcc.tienda.modules.purchasing.application.model;

import java.time.Instant;
import java.util.UUID;

public record Supplier(
    UUID supplierId,
    String supplierCode,
    String legalName,
    String tradeName,
    String taxId,
    String email,
    String phone,
    int creditDays,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
