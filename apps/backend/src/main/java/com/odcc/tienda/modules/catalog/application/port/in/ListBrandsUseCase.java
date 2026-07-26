package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;

public interface ListBrandsUseCase {

    BrandPage execute(ListBrandsQuery query);
}
