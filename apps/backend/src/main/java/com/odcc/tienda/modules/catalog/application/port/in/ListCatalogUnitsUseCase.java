package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;

import java.util.List;

public interface ListCatalogUnitsUseCase {
    List<CatalogUnit> execute();
}
