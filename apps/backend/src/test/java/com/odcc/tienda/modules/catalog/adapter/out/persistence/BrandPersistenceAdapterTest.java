package com.odcc.tienda.modules.catalog.adapter.out.persistence;

import com.odcc.tienda.TestcontainersConfiguration;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.application.query.BrandPage;
import com.odcc.tienda.modules.catalog.application.query.BrandSortField;
import com.odcc.tienda.modules.catalog.application.query.ListBrandsQuery;
import com.odcc.tienda.modules.catalog.application.query.SortDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class BrandPersistenceAdapterTest {

    @Autowired
    private BrandRepositoryPort brandRepository;

    @Test
    void shouldSaveBrandInPostgreSql() {
        Brand brand = Brand.create(
            "TEST-BRAND",
            "Marca de prueba"
        );

        Brand savedBrand = brandRepository.save(brand);

        assertNotNull(savedBrand.getId());
        assertEquals("TEST-BRAND", savedBrand.getCode());
        assertEquals("Marca de prueba", savedBrand.getName());
        assertEquals(BrandStatus.ACTIVE, savedBrand.getStatus());
        assertNotNull(savedBrand.getCreatedAt());

        assertTrue(
            brandRepository.existsByCode("TEST-BRAND")
        );
    }

    @Test
    void shouldRejectDuplicatedCodeInPostgreSql() {
        Brand firstBrand = Brand.create(
            "DUPLICATED-BRAND",
            "Primera marca"
        );

        Brand duplicatedBrand = Brand.create(
            "DUPLICATED-BRAND",
            "Segunda marca"
        );

        brandRepository.save(firstBrand);

        assertThrows(
            BrandCodeAlreadyExistsException.class,
            () -> brandRepository.save(duplicatedBrand)
        );
    }

    @Test
    void shouldFindBrandByIdInPostgreSql() {
        Brand brand = brandRepository.save(
            Brand.create("FIND-BRAND", "Marca consultable")
        );

        Optional<Brand> foundBrand = brandRepository.findById(
            brand.getId()
        );

        assertTrue(foundBrand.isPresent());
        assertEquals(brand.getId(), foundBrand.get().getId());
        assertEquals("FIND-BRAND", foundBrand.get().getCode());
        assertEquals("Marca consultable", foundBrand.get().getName());
    }

    @Test
    void shouldReturnEmptyWhenBrandDoesNotExistInPostgreSql() {
        Optional<Brand> foundBrand = brandRepository.findById(
            UUID.fromString("3f46c1b5-2ab6-45b5-b4ee-cfc35f5c0f35")
        );

        assertTrue(foundBrand.isEmpty());
    }

    @Test
    void shouldFilterSortAndPaginateBrandsInPostgreSql() {
        brandRepository.save(Brand.create("PERSIST-PEPSI", "Pepsi"));
        brandRepository.save(Brand.create("PERSIST-COCA", "Coca Cola"));
        brandRepository.save(
            Brand.create("PERSIST-INACTIVE", "Cola inactiva")
                .changeStatus(BrandStatus.INACTIVE)
        );

        BrandPage page = brandRepository.findAll(
            new ListBrandsQuery(
                0,
                10,
                "cola",
                BrandStatus.ACTIVE,
                BrandSortField.NAME,
                SortDirection.ASC
            )
        );

        assertEquals(1, page.totalElements());
        assertEquals("PERSIST-COCA", page.content().getFirst().getCode());
    }

    @Test
    void shouldUpdateExistingBrandInPostgreSql() {
        Brand saved = brandRepository.save(
            Brand.create("PERSIST-OLD", "Nombre anterior")
        );

        Brand updated = brandRepository.save(
            saved.update("PERSIST-NEW", "Nombre actualizado")
        );

        assertEquals(saved.getId(), updated.getId());
        assertEquals("PERSIST-NEW", updated.getCode());
        assertEquals("Nombre actualizado", updated.getName());
    }

    @Test
    void shouldExcludeCurrentBrandWhenCheckingDuplicatedCode() {
        Brand saved = brandRepository.save(
            Brand.create("PERSIST-EXCLUDE", "Marca")
        );

        boolean duplicated = brandRepository.existsByCodeAndIdNot(
            saved.getCode(),
            saved.getId()
        );

        assertEquals(false, duplicated);
    }
}
