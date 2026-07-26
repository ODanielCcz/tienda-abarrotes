package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateBrandCommand;
import com.odcc.tienda.modules.catalog.application.exception.BrandCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.BrandNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Brand;
import com.odcc.tienda.modules.catalog.support.InMemoryBrandRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateBrandServiceTest {

    private InMemoryBrandRepository repository;
    private UpdateBrandService service;
    private Brand brand;
    private ImmediateTransactionRunner transactionRunner;
    private InMemoryBusinessAuditPort auditPort;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBrandRepository();
        transactionRunner = new ImmediateTransactionRunner();
        auditPort = new InMemoryBusinessAuditPort();
        service = new UpdateBrandService(
            repository,
            transactionRunner,
            auditPort
        );
        brand = repository.save(Brand.create("ORIGINAL", "Original"));
    }

    @Test
    void shouldUpdateAndNormalizeBrand() {
        Brand updated = service.execute(
            new UpdateBrandCommand(
                brand.getId(),
                " updated ",
                " Marca actualizada "
            )
        );

        assertEquals(brand.getId(), updated.getId());
        assertEquals("UPDATED", updated.getCode());
        assertEquals("Marca actualizada", updated.getName());
        assertEquals(1, transactionRunner.executionCount());
        assertEquals("BRAND_UPDATED", auditPort.events().getFirst().eventType());
        assertEquals("ORIGINAL", auditPort.events().getFirst().beforeState().get("code"));
        assertEquals("UPDATED", auditPort.events().getFirst().afterState().get("code"));
    }

    @Test
    void shouldAllowKeepingTheSameCode() {
        Brand updated = service.execute(
            new UpdateBrandCommand(
                brand.getId(),
                "original",
                "Nuevo nombre"
            )
        );

        assertEquals("ORIGINAL", updated.getCode());
        assertEquals("Nuevo nombre", updated.getName());
    }

    @Test
    void shouldRejectCodeUsedByAnotherBrand() {
        repository.save(Brand.create("EXISTING", "Existente"));

        assertThrows(
            BrandCodeAlreadyExistsException.class,
            () -> service.execute(
                new UpdateBrandCommand(
                    brand.getId(),
                    "EXISTING",
                    "Duplicada"
                )
            )
        );
    }

    @Test
    void shouldRejectUnknownBrand() {
        assertThrows(
            BrandNotFoundException.class,
            () -> service.execute(
                new UpdateBrandCommand(
                    UUID.fromString("8f8e1393-93d4-4521-8294-dc9431629d56"),
                    "UNKNOWN",
                    "Desconocida"
                )
            )
        );
    }
}
