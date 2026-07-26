package com.odcc.tienda.modules.catalog.application.query;

import com.odcc.tienda.modules.catalog.domain.model.Brand;

import java.util.List;

public record BrandPage(
    List<Brand> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public BrandPage {
        content = List.copyOf(content);
    }
}
