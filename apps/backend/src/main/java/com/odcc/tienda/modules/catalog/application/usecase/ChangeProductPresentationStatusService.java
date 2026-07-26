package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeProductPresentationStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.ProductPresentationNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductPresentationStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class ChangeProductPresentationStatusService implements ChangeProductPresentationStatusUseCase {
    private final ProductPresentationRepositoryPort repository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;
    public ProductPresentation execute(ChangeProductPresentationStatusCommand command) {
        return transactionRunner.required(() -> {
            ProductPresentation current = repository.findById(command.presentationId()).orElseThrow(() -> new ProductPresentationNotFoundException(command.presentationId()));
            ProductPresentation saved = repository.save(current.changeStatus(command.status()));
            auditPort.record(new BusinessAuditEvent("PRODUCT_PRESENTATION_STATUS_CHANGED", "PRODUCT_PRESENTATION", saved.getId(), CreateProductPresentationService.state(current), CreateProductPresentationService.state(saved), Map.of()));
            return saved;
        });
    }
}
