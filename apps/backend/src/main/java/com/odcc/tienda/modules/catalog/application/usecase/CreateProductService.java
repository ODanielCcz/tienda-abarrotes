package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateProductCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductBrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.ProductCategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class CreateProductService implements CreateProductUseCase {

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;
    private final BrandRepositoryPort brandRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Product execute(CreateProductCommand command) {
        return transactionRunner.required(() -> create(command));
    }

    private Product create(CreateProductCommand command) {
        Objects.requireNonNull(command, "El comando de creacion es obligatorio");
        validateCategoryExists(command.categoryId());
        validateBrandExists(command.brandId());

        Product product = Product.create(
            command.categoryId(),
            command.brandId(),
            command.name(),
            command.description(),
            command.productType(),
            command.tracksInventory(),
            command.tracksLots(),
            command.tracksExpiration()
        );

        Product savedProduct = productRepository.save(product);
        auditPort.record(new BusinessAuditEvent(
            "PRODUCT_CREATED",
            "PRODUCT",
            savedProduct.getId(),
            Map.of(),
            stateOf(savedProduct),
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

    static Map<String, Object> stateOf(Product product) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("name", product.getName());
        if (product.getCategoryId() != null) state.put("categoryId", product.getCategoryId());
        if (product.getBrandId() != null) state.put("brandId", product.getBrandId());
        if (product.getDescription() != null) state.put("description", product.getDescription());
        state.put("productType", product.getProductType().name());
        state.put("tracksInventory", product.isTracksInventory());
        state.put("tracksLots", product.isTracksLots());
        state.put("tracksExpiration", product.isTracksExpiration());
        state.put("status", product.getStatus().name());
        return state;
    }
}
