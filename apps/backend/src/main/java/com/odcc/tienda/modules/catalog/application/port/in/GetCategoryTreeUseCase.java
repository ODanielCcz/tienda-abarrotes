package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.model.CategoryTreeNode;
import com.odcc.tienda.modules.catalog.application.query.CategoryTreeQuery;

import java.util.List;

public interface GetCategoryTreeUseCase {

    List<CategoryTreeNode> execute(CategoryTreeQuery query);
}
