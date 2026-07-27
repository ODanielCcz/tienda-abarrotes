package com.odcc.tienda.modules.sales.application.model;

import java.time.Instant;
import java.util.UUID;

public record Customer(
    UUID customerId,
    String customerCode,
    String customerType,
    String displayName,
    String email,
    String phone,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
