package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateProductCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductBrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductCategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class UpdateProductService implements UpdateProductUseCase {

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final BrandRepositoryPort brandRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Product execute(UpdateProductCommand command) {
        return transactionRunner.required(() -> update(command));
    }

    private Product update(UpdateProductCommand command) {
        Objects.requireNonNull(command, "El comando de actualizacion es obligatorio");
        Objects.requireNonNull(command.productId(), "El id del producto es obligatorio");

        Product currentProduct = productRepository
            .findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        validateCategoryExists(command.categoryId());
        validateBrandExists(command.brandId());

        Product updatedProduct = currentProduct.update(
            command.categoryId(),
            command.brandId(),
            command.name(),
            command.description(),
            command.productType(),
            command.tracksInventory(),
            command.tracksLots(),
            command.tracksExpiration()
        );

        Product savedProduct = productRepository.save(updatedProduct);
        auditPort.record(new BusinessAuditEvent(
            "PRODUCT_UPDATED",
            "PRODUCT",
            savedProduct.getId(),
            CreateProductService.stateOf(currentProduct),
            CreateProductService.stateOf(savedProduct),
            Map.of()
        ));
        return savedProduct;
    }

    private void validateCategoryExists(UUID categoryId) {
        if (categoryId != null && categoryRepository.findById(categoryId).isEmpty()) {
            throw new ProductCategoryNotFoundException(categoryId);
        }
    }

    private void validateBrandExists(UUID brandId) {
        if (brandId != null && brandRepository.findById(brandId).isEmpty()) {
            throw new ProductBrandNotFoundException(brandId);
        }
    }
}
