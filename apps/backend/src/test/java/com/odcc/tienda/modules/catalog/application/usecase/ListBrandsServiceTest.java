package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.BrandSortField;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListBrandsServiceTest {

    private InMemoryBrandRepository repository;
    private ListBrandsService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBrandRepository();
        service = new ListBrandsService(repository);

        repository.save(Brand.create("PEPSI", "Pepsi"));
        repository.save(Brand.create("COCA-COLA", "Coca Cola"));
        repository.save(
            Brand.create("COLA-INACTIVE", "Cola inactiva")
                .changeStatus(BrandStatus.INACTIVE)
        );
    }

    @Test
    void shouldFilterSortAndPaginateBrands() {
        ListBrandsQuery query = new ListBrandsQuery(
            0,
            1,
            "cola",
            BrandStatus.ACTIVE,
            BrandSortField.NAME,
            SortDirection.ASC
        );

        BrandPage result = service.execute(query);

        assertEquals(1, result.content().size());
        assertEquals("COCA-COLA", result.content().getFirst().getCode());
        assertEquals(1, result.totalElements());
        assertEquals(1, result.totalPages());
    }

    @Test
    void shouldRejectAnUnboundedPageSize() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ListBrandsQuery(
                0,
                101,
                null,
                null,
                null,
                null
            )
        );
    }
}
