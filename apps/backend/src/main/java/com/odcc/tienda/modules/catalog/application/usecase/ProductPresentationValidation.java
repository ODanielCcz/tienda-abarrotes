package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.ProductNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationSkuAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.TaxNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.UnitOfMeasureNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;

import java.util.UUID;

final class ProductPresentationValidation {
    private ProductPresentationValidation() {}

    static void productExists(ProductRepositoryPort productRepository, UUID productId) {
        if (productRepository.findById(productId).isEmpty()) throw new ProductNotFoundException(productId);
    }

    static void presentationExists(ProductPresentationRepositoryPort repository, UUID presentationId) {
        if (repository.findById(presentationId).isEmpty()) throw new ProductPresentationNotFoundException(presentationId);
    }

    static void unitExists(ProductPresentationRepositoryPort repository, UUID unitId) {
        if (!repository.existsUnitById(unitId)) throw new UnitOfMeasureNotFoundException(unitId);
    }

    static void taxExists(ProductPresentationRepositoryPort repository, UUID taxId) {
        if (taxId != null && !repository.existsTaxById(taxId)) throw new TaxNotFoundException(taxId);
    }

    static void skuAvailable(ProductPresentationRepositoryPort repository, String sku) {
        if (repository.existsBySku(sku)) throw new ProductPresentationSkuAlreadyExistsException(sku);
    }

    static void skuAvailableForUpdate(ProductPresentationRepositoryPort repository, String sku, UUID presentationId) {
        if (repository.existsBySkuAndIdNot(sku, presentationId)) throw new ProductPresentationSkuAlreadyExistsException(sku);
    }
}
