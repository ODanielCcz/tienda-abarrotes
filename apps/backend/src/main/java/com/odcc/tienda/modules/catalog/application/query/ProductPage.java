package com.odcc.tienda.modules.catalog.application.query;

import com.odcc.tienda.modules.catalog.domain.model.Product;

import java.util.List;

public record ProductPage(
    List<Product> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}
