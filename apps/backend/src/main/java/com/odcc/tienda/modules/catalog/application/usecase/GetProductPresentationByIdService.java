package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductPresentationByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public final class GetProductPresentationByIdService implements GetProductPresentationByIdUseCase {
    private final ProductPresentationRepositoryPort repository;
    public ProductPresentation execute(UUID presentationId) {
        return repository.findById(presentationId).orElseThrow(() -> new ProductPresentationNotFoundException(presentationId));
    }
}
