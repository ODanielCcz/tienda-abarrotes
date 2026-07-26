package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.in.CreateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RequiredArgsConstructor
public final class CreateBrandService implements CreateBrandUseCase {

    private final BrandRepositoryPort brandRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Brand execute(CreateBrandCommand command) {
        return transactionRunner.required(() -> create(command));
    }

    private Brand create(CreateBrandCommand command) {
        Brand brand = Brand.create(
            command.code(),
            command.name()
        );

        if (brandRepository.existsByCode(brand.getCode())) {
            throw new BrandCodeAlreadyExistsException(brand.getCode());
        }

        Brand savedBrand = brandRepository.save(brand);

        auditPort.record(
            new BusinessAuditEvent(
                "BRAND_CREATED",
                "BRAND",
                savedBrand.getId(),
                Map.of(),
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
