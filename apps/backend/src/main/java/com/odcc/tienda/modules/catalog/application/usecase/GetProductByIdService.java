package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.ProductNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class GetProductByIdService implements GetProductByIdUseCase {

    private final ProductRepositoryPort productRepository;

    @Override
    public Product execute(UUID productId) {
        Objects.requireNonNull(productId, "El id del producto es obligatorio");
        return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
