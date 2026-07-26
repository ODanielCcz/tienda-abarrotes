package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.GetBrandByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import lombok.RequiredArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class GetBrandByIdService implements GetBrandByIdUseCase {

    private final BrandRepositoryPort brandRepository;

    @Override
    public Brand execute(UUID brandId) {
        Objects.requireNonNull(
            brandId,
            "El id de la marca es obligatorio"
        );

        return brandRepository
            .findById(brandId)
            .orElseThrow(
                () -> new BrandNotFoundException(brandId)
            );
    }
}
