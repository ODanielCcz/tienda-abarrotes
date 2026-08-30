package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;

import java.util.List;
import java.util.UUID;

public interface CatalogPriceRepositoryPort {
    List<CatalogPrice> findEffectiveGeneralPrices(UUID branchId);
}
