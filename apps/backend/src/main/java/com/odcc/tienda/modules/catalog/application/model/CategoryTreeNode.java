package com.odcc.tienda.modules.catalog.application.model;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CategoryTreeNode(
    UUID id,
    UUID parentCategoryId,
    String code,
    String name,
    CategoryStatus status,
    Instant createdAt,
    Instant updatedAt,
    List<CategoryTreeNode> children
) {

    public CategoryTreeNode {
        Objects.requireNonNull(id, "El id de la categoria es obligatorio");
        Objects.requireNonNull(code, "El codigo de la categoria es obligatorio");
        Objects.requireNonNull(name, "El nombre de la categoria es obligatorio");
        Objects.requireNonNull(status, "El estado de la categoria es obligatorio");
        Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");
        children = children == null ? List.of() : List.copyOf(children);
    }
}
