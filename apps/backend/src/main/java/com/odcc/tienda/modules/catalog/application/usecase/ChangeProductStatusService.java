package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeProductStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Product;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public final class ChangeProductStatusService implements ChangeProductStatusUseCase {

    private final ProductRepositoryPort productRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Product execute(ChangeProductStatusCommand command) {
        return transactionRunner.required(() -> changeStatus(command));
    }

    private Product changeStatus(ChangeProductStatusCommand command) {
        Objects.requireNonNull(command, "El comando de cambio de estado es obligatorio");
        Objects.requireNonNull(command.productId(), "El id del producto es obligatorio");

        Product currentProduct = productRepository
            .findById(command.productId())
            .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        Product updatedProduct = currentProduct.changeStatus(command.status());
        Product savedProduct = productRepository.save(updatedProduct);
        auditPort.record(new BusinessAuditEvent(
            "PRODUCT_STATUS_CHANGED",
            "PRODUCT",
            savedProduct.getId(),
            CreateProductService.stateOf(currentProduct),
            CreateProductService.stateOf(savedProduct),
            Map.of()
        ));
        return savedProduct;
    }
}
