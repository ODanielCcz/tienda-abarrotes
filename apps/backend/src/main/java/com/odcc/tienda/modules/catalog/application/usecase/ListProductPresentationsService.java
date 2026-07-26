package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.port.in.ListProductPresentationsUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public final class ListProductPresentationsService implements ListProductPresentationsUseCase {
    private final ProductPresentationRepositoryPort presentationRepository;
    private final ProductRepositoryPort productRepository;
    public List<ProductPresentation> execute(UUID productId) {
        ProductPresentationValidation.productExists(productRepository, productId);
        return presentationRepository.findByProductId(productId);
    }
}
