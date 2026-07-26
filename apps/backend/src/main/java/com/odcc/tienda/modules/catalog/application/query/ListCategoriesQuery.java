package com.odcc.tienda.modules.catalog.application.query;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

public record ListCategoriesQuery(
    int page,
    int size,
    String search,
    CategoryStatus status,
    CategorySortField sortBy,
    SortDirection direction
) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public ListCategoriesQuery {
        if (page < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("El tamano de pagina debe estar entre 1 y 100");
        }
        search = normalizeSearch(search);
        sortBy = sortBy == null ? CategorySortField.NAME : sortBy;
        direction = direction == null ? SortDirection.ASC : direction;
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }
}