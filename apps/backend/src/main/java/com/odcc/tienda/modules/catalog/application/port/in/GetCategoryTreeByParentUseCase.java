package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;

import java.util.UUID;

public interface GetCategoryTreeByParentUseCase {

    CategoryTreeNode execute(UUID rootCategoryId, CategoryTreeQuery query);
}
