package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateBrandCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.domain.model.BrandStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CreateBrandServiceTest {

    private InMemoryBrandRepository repository;
    private CreateBrandService service;
    private ImmediateTransactionRunner transactionRunner;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBrandRepository();
        transactionRunner = new ImmediateTransactionRunner();
        auditPort = new InMemoryBusinessAuditPort();
        service = new CreateBrandService(
            repository,
            transactionRunner,
            auditPort
        );
    }

    @Test
    void shouldCreateAnActiveBrand() {
        CreateBrandCommand command = new CreateBrandCommand(
            "coca-cola",
            "Coca Cola"
        );

        Brand createdBrand = service.execute(command);

        assertNotNull(createdBrand.getId());
        assertEquals("COCA-COLA", createdBrand.getCode());
        assertEquals("Coca Cola", createdBrand.getName());
        assertEquals(BrandStatus.ACTIVE, createdBrand.getStatus());
        assertNotNull(createdBrand.getCreatedAt());

        assertTrue(repository.existsByCode("COCA-COLA"));
        assertEquals(1, transactionRunner.executionCount());
        assertEquals(1, auditPort.events().size());
        assertEquals("BRAND_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectDuplicatedBrandCode() {
        service.execute(
            new CreateBrandCommand("coca-cola", "Coca Cola")
        );

        CreateBrandCommand duplicatedCommand =
            new CreateBrandCommand("COCA-COLA", "Coca Cola");

        assertThrows(
            BrandCodeAlreadyExistsException.class,
            () -> service.execute(duplicatedCommand)
        );

        assertEquals(1, auditPort.events().size());
    }
}
