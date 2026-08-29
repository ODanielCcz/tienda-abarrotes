package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogTaxesUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CatalogReferenceDataRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.CatalogTax;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class ListCatalogTaxesService implements ListCatalogTaxesUseCase {
    private final CatalogReferenceDataRepositoryPort repository;

    @Override
    public List<CatalogTax> execute() {
        return repository.findAllActiveTaxes();
    }
}
