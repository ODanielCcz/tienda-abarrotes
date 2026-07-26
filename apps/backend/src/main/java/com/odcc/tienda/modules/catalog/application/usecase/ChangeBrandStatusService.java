package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeBrandStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeBrandStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Map;

@RequiredArgsConstructor
public final class ChangeBrandStatusService implements ChangeBrandStatusUseCase {

    private final BrandRepositoryPort brandRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Brand execute(ChangeBrandStatusCommand command) {
        return transactionRunner.required(() -> changeStatus(command));
    }

    private Brand changeStatus(ChangeBrandStatusCommand command) {
        Objects.requireNonNull(command, "El comando de estado es obligatorio");
        Objects.requireNonNull(command.brandId(), "El id de la marca es obligatorio");
        Objects.requireNonNull(command.status(), "El estado de la marca es obligatorio");

        Brand brand = brandRepository
            .findById(command.brandId())
            .orElseThrow(() -> new BrandNotFoundException(command.brandId()));

        Brand changedBrand = brand.changeStatus(command.status());

        if (changedBrand == brand) {
            return brand;
        }

        Brand savedBrand = brandRepository.save(changedBrand);

        auditPort.record(
            new BusinessAuditEvent(
                "BRAND_STATUS_CHANGED",
                "BRAND",
                savedBrand.getId(),
                Map.of("status", brand.getStatus().name()),
                Map.of("status", savedBrand.getStatus().name()),
                Map.of()
            )
        );

        return savedBrand;
    }
}
