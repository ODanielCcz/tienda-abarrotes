package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListCategoriesUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public final class ListCategoriesService implements ListCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public CategoryPage execute(ListCategoriesQuery query) {
        Objects.requireNonNull(query, "La consulta de categorias es obligatoria");
        return categoryRepository.findAll(query);
    }
}