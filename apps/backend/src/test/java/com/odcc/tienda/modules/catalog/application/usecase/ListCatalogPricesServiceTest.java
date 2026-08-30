package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.model.CatalogPrice;
import com.odcc.tienda.modules.catalog.application.port.out.CatalogPriceRepositoryPort;
import com.odcc.tienda.shared.application.authorization.BranchAccessPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListCatalogPricesServiceTest {

    @Test
    void authorizesTheBranchAndReturnsItsEffectiveGeneralPrices() {
        CatalogPriceRepositoryPort repository = mock(CatalogPriceRepositoryPort.class);
        BranchAccessPort branchAccess = mock(BranchAccessPort.class);
        UUID userId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        CatalogPrice price = new CatalogPrice(
            UUID.randomUUID(), UUID.randomUUID(), branchId, new BigDecimal("18.5000"), "MXN"
        );
        given(repository.findEffectiveGeneralPrices(branchId)).willReturn(List.of(price));
        ListCatalogPricesService service = new ListCatalogPricesService(repository, branchAccess);

        List<CatalogPrice> result = service.execute(branchId, userId);

        assertThat(result).containsExactly(price);
        verify(branchAccess).requireAccess(userId, branchId);
    }
}
