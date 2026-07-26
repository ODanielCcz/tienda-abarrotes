package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListProductsUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.application.query.ListProductsQuery;
import com.odcc.tienda.modules.catalog.application.query.ProductPage;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public final class ListProductsService implements ListProductsUseCase {

    private final ProductRepositoryPort productRepository;

    @Override
    public ProductPage execute(ListProductsQuery query) {
        Objects.requireNonNull(query, "La consulta de productos es obligatoria");
        return productRepository.findAll(query);
    }
}
