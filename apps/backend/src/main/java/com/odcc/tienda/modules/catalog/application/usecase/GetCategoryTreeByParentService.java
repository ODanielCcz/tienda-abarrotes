package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeByParentUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public final class GetCategoryTreeByParentService implements GetCategoryTreeByParentUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public CategoryTreeNode execute(UUID rootCategoryId, CategoryTreeQuery query) {
        CategoryTreeNode root = CategoryTreeAssembler.assembleRoot(
            rootCategoryId,
            categoryRepository.findDescendantsForTree(rootCategoryId, query.status())
        );

        if (root == null) {
            throw new CategoryNotFoundException(rootCategoryId);
        }

        return root;
    }
}
