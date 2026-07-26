package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.query.CategoryPage;
import com.odcc.tienda.modules.catalog.application.query.ListCategoriesQuery;

public interface ListCategoriesUseCase {

    CategoryPage execute(ListCategoriesQuery query);
}