package com.odcc.tienda.modules.catalog.application.query;

import com.odcc.tienda.modules.catalog.domain.model.Category;

import java.util.List;

public record CategoryPage(
    List<Category> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}