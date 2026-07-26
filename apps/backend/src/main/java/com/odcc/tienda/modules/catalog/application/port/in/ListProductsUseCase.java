package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;

public interface ListProductsUseCase {
    ProductPage execute(ListProductsQuery query);
}
