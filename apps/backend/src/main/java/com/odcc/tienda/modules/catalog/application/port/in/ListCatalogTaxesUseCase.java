package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;

import java.util.List;

public interface ListCatalogTaxesUseCase {
    List<CatalogTax> execute();
}
