package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;
import com.odcc.tienda.modules.catalog.application.port.in.ListCatalogPricesUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CatalogPriceRepositoryPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ListCatalogPricesService implements ListCatalogPricesUseCase {
    private final CatalogPriceRepositoryPort repository;
    private final BranchAccessPort branchAccessPort;

    @Override
    public List<CatalogPrice> execute(UUID branchId, UUID userId) {
        if (branchId == null) throw new IllegalArgumentException("La sucursal es obligatoria");
        if (userId == null) throw new IllegalArgumentException("El usuario es obligatorio");
        branchAccessPort.requireAccess(userId, branchId);
        return repository.findEffectiveGeneralPrices(branchId);
    }
}
