package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
    UUID id,
    UUID parentCategoryId,
    String code,
    String name,
    CategoryStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<CategoryTreeResponse> children
) {
}
