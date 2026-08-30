package com.odcc.tienda.modules.catalog.application.port.in;

import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;

import java.util.List;
import java.util.UUID;

public interface ListCatalogPricesUseCase {
    List<CatalogPrice> execute(UUID branchId, UUID userId);
}
