package com.odcc.tienda.modules.catalog.application.port.out;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPresentationRepositoryPort {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID excludedPresentationId);
    boolean existsUnitById(UUID unitId);
    boolean existsTaxById(UUID taxId);
    Optional<ProductPresentation> findById(UUID presentationId);
    List<ProductPresentation> findByProductId(UUID productId);
    ProductPresentation save(ProductPresentation presentation);
}
