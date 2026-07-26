package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import java.time.Instant;
import java.util.UUID;

public record BrandResponse(
    UUID id,
    String code,
    String name,
    String status,
    Instant createdAt
) {
}
