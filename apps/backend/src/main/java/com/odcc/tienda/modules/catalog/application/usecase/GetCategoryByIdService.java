package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class GetCategoryByIdService implements GetCategoryByIdUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public Category execute(UUID categoryId) {
        Objects.requireNonNull(categoryId, "El id de la categoria es obligatorio");
        return categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }
}