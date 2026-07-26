package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.Category;

import java.util.UUID;

public interface GetCategoryByIdUseCase {

    Category execute(UUID categoryId);
}