package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListBrandsUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public final class ListBrandsService implements ListBrandsUseCase {

    private final BrandRepositoryPort brandRepository;

    @Override
    public BrandPage execute(ListBrandsQuery query) {
        Objects.requireNonNull(query, "La consulta de marcas es obligatoria");

        return brandRepository.findAll(query);
    }
}
