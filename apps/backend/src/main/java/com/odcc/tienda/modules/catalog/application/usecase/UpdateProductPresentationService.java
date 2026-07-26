package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class UpdateProductPresentationService implements UpdateProductPresentationUseCase {
    private final ProductPresentationRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    public ProductPresentation execute(UpdateProductPresentationCommand command) {
        return transactionRunner.required(() -> {
            ProductPresentation current = repository.findById(command.presentationId()).orElseThrow(() -> new ProductPresentationNotFoundException(command.presentationId()));
            ProductPresentationValidation.unitExists(repository, command.unitId());
            ProductPresentationValidation.taxExists(repository, command.taxId());
            ProductPresentation updated = current.update(command.unitId(), command.taxId(), command.sku(), command.name(), command.conversionFactor(), command.netContent(), command.minimumStock());
            ProductPresentationValidation.skuAvailableForUpdate(repository, updated.getSku(), updated.getId());
            ProductPresentation saved = repository.save(updated);
            auditPort.record(new BusinessAuditEvent("PRODUCT_PRESENTATION_UPDATED", "PRODUCT_PRESENTATION", saved.getId(), CreateProductPresentationService.state(current), CreateProductPresentationService.state(saved), Map.of()));
            return saved;
        });
    }
}
