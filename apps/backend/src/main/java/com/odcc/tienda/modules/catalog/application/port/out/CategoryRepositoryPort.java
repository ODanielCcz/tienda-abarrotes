package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepositoryPort {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID excludedCategoryId);

    Optional<Category> findById(UUID categoryId);

    boolean hasAncestor(UUID categoryId, UUID ancestorCategoryId);

    CategoryPage findAll(ListCategoriesQuery query);

    List<Category> findAllForTree(CategoryStatus status);

    List<Category> findDescendantsForTree(UUID rootCategoryId, CategoryStatus status);

    Category save(Category category);
}
