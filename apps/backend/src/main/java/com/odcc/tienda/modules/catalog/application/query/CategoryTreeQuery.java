package com.odcc.tienda.modules.catalog.application.query;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

public record CategoryTreeQuery(
    CategoryStatus status
) {
}
