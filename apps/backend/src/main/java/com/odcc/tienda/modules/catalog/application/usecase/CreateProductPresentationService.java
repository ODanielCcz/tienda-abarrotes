package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateProductPresentationCommand;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.ProductPresentation;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class CreateProductPresentationService implements CreateProductPresentationUseCase {
    private final ProductPresentationRepositoryPort presentationRepository;
    private final ProductRepositoryPort productRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public ProductPresentation execute(CreateProductPresentationCommand command) {
        return transactionRunner.required(() -> {
            ProductPresentationValidation.productExists(productRepository, command.productId());
            ProductPresentationValidation.unitExists(presentationRepository, command.unitId());
            ProductPresentationValidation.taxExists(presentationRepository, command.taxId());
            ProductPresentation presentation = ProductPresentation.create(command.productId(), command.unitId(), command.taxId(), command.sku(), command.name(), command.conversionFactor(), command.netContent(), command.minimumStock());
            ProductPresentationValidation.skuAvailable(presentationRepository, presentation.getSku());
            ProductPresentation saved = presentationRepository.save(presentation);
            auditPort.record(new BusinessAuditEvent("PRODUCT_PRESENTATION_CREATED", "PRODUCT_PRESENTATION", saved.getId(), null, state(saved), Map.of()));
            return saved;
        });
    }

    static Map<String, Object> state(ProductPresentation presentation) {
        return Map.of(
            "id", presentation.getId(),
            "productId", presentation.getProductId(),
            "unitId", presentation.getUnitId(),
            "sku", presentation.getSku(),
            "name", presentation.getName(),
            "status", presentation.getStatus().name()
        );
    }
}
