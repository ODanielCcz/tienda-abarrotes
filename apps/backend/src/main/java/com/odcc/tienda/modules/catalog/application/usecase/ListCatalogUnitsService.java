package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogUnitsUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CatalogReferenceDataRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.CatalogUnit;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class ListCatalogUnitsService implements ListCatalogUnitsUseCase {
    private final CatalogReferenceDataRepositoryPort repository;

    @Override
    public List<CatalogUnit> execute() {
        return repository.findAllUnits();
    }
}
