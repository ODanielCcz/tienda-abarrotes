package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class GetCategoryTreeService implements GetCategoryTreeUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public List<CategoryTreeNode> execute(CategoryTreeQuery query) {
        return CategoryTreeAssembler.assembleForest(
            categoryRepository.findAllForTree(query.status())
        );
    }
}
