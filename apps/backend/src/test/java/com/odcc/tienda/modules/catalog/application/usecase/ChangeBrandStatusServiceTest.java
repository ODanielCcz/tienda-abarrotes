package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeBrandStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeBrandStatusServiceTest {

    private InMemoryBrandRepository repository;
    private ChangeBrandStatusService service;
    private Brand brand;
    private ImmediateTransactionRunner transactionRunner;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBrandRepository();
        transactionRunner = new ImmediateTransactionRunner();
        auditPort = new InMemoryBusinessAuditPort();
        service = new ChangeBrandStatusService(
            repository,
            transactionRunner,
            auditPort
        );
        brand = repository.save(Brand.create("STATUS", "Estado"));
    }

    @Test
    void shouldDeactivateBrand() {
        Brand changed = service.execute(
            new ChangeBrandStatusCommand(
                brand.getId(),
                BrandStatus.INACTIVE
            )
        );

        assertEquals(BrandStatus.INACTIVE, changed.getStatus());
        assertEquals(2, repository.saveCount());
        assertEquals(1, transactionRunner.executionCount());
        assertEquals(
            "BRAND_STATUS_CHANGED",
            auditPort.events().getFirst().eventType()
        );
    }

    @Test
    void shouldReactivateBrand() {
        repository.save(brand.changeStatus(BrandStatus.INACTIVE));

        Brand changed = service.execute(
            new ChangeBrandStatusCommand(
                brand.getId(),
                BrandStatus.ACTIVE
            )
        );

        assertEquals(BrandStatus.ACTIVE, changed.getStatus());
        assertEquals(3, repository.saveCount());
        assertEquals(1, transactionRunner.executionCount());
        assertEquals(
            "ACTIVE",
            auditPort.events().getFirst().afterState().get("status")
        );
    }

    @Test
    void shouldNotPersistWhenStatusIsAlreadyApplied() {
        Brand unchanged = service.execute(
            new ChangeBrandStatusCommand(
                brand.getId(),
                BrandStatus.ACTIVE
            )
        );

        assertEquals(BrandStatus.ACTIVE, unchanged.getStatus());
        assertEquals(1, repository.saveCount());
        assertEquals(0, auditPort.events().size());
    }

    @Test
    void shouldRejectUnknownBrand() {
        assertThrows(
            BrandNotFoundException.class,
            () -> service.execute(
                new ChangeBrandStatusCommand(
                    UUID.fromString("a5ba8b45-56c1-4470-a431-d5ff49fa92dd"),
                    BrandStatus.INACTIVE
                )
            )
        );
    }
}
