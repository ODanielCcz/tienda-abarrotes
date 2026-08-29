package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;
import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;

import java.util.List;

public interface CatalogReferenceDataRepositoryPort {
    List<CatalogUnit> findAllUnits();

    List<CatalogTax> findAllActiveTaxes();
}
