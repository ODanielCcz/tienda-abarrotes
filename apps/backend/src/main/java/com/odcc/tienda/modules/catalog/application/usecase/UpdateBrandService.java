package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.Map;

@RequiredArgsConstructor
public final class UpdateBrandService implements UpdateBrandUseCase {

    private final BrandRepositoryPort brandRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Brand execute(UpdateBrandCommand command) {
        return transactionRunner.required(() -> update(command));
    }

    private Brand update(UpdateBrandCommand command) {
        Objects.requireNonNull(command, "El comando de actualización es obligatorio");
        Objects.requireNonNull(command.brandId(), "El id de la marca es obligatorio");

        Brand currentBrand = brandRepository
            .findById(command.brandId())
            .orElseThrow(() -> new BrandNotFoundException(command.brandId()));

        Brand updatedBrand = currentBrand.update(
            command.code(),
            command.name()
        );

        boolean codeChanged = !currentBrand
            .getCode()
            .equals(updatedBrand.getCode());

        if (
            codeChanged
                && brandRepository.existsByCodeAndIdNot(
                    updatedBrand.getCode(),
                    updatedBrand.getId()
                )
        ) {
            throw new BrandCodeAlreadyExistsException(updatedBrand.getCode());
        }

        Brand savedBrand = brandRepository.save(updatedBrand);

        auditPort.record(
            new BusinessAuditEvent(
                "BRAND_UPDATED",
                "BRAND",
                savedBrand.getId(),
                stateOf(currentBrand),
                stateOf(savedBrand),
                Map.of()
            )
        );

        return savedBrand;
    }

    private static Map<String, Object> stateOf(Brand brand) {
        return Map.of(
            "code", brand.getCode(),
            "name", brand.getName(),
            "status", brand.getStatus().name()
        );
    }
}
