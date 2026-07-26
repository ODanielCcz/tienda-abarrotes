package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetBrandByIdServiceTest {

    private InMemoryBrandRepository repository;
    private GetBrandByIdService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBrandRepository();
        service = new GetBrandByIdService(repository);
    }

    @Test
    void shouldReturnBrandWhenItExists() {
        UUID brandId = UUID.fromString(
            "58f04c9b-7847-416a-95bc-a101600c861a"
        );

        Brand brand = Brand.restore(
            brandId,
            "COCA-COLA",
            "Coca Cola",
            BrandStatus.ACTIVE,
            Instant.parse("2026-07-14T06:00:00Z")
        );

        repository.save(brand);

        Brand foundBrand = service.execute(brandId);

        assertEquals(brand, foundBrand);
    }

    @Test
    void shouldRejectUnknownBrandId() {
        UUID brandId = UUID.fromString(
            "2bd82719-dc66-4100-b6bb-d8c4abddc703"
        );

        BrandNotFoundException exception = assertThrows(
            BrandNotFoundException.class,
            () -> service.execute(brandId)
        );

        assertEquals(
            "No existe una marca con el id: " + brandId,
            exception.getMessage()
        );
    }
}
