package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeCategoryStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeCategoryStatusServiceTest {

    private InMemoryCategoryRepository repository;
    private InMemoryBusinessAuditPort auditPort;
    private ChangeCategoryStatusService service;
    private Category category;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new ChangeCategoryStatusService(repository, new ImmediateTransactionRunner(), auditPort);
        category = repository.save(Category.create("STATUS", "Estado", null));
    }

    @Test
    void shouldDeactivateCategory() {
        Category changed = service.execute(new ChangeCategoryStatusCommand(category.getId(), CategoryStatus.INACTIVE));

        assertEquals(CategoryStatus.INACTIVE, changed.getStatus());
        assertEquals("CATEGORY_STATUS_CHANGED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldReactivateCategory() {
        repository.save(category.changeStatus(CategoryStatus.INACTIVE));

        Category changed = service.execute(new ChangeCategoryStatusCommand(category.getId(), CategoryStatus.ACTIVE));

        assertEquals(CategoryStatus.ACTIVE, changed.getStatus());
    }

    @Test
    void shouldNotSaveWhenStatusDoesNotChange() {
        Category unchanged = service.execute(new ChangeCategoryStatusCommand(category.getId(), CategoryStatus.ACTIVE));

        assertEquals(CategoryStatus.ACTIVE, unchanged.getStatus());
        assertEquals(1, repository.saveCount());
        assertEquals(0, auditPort.events().size());
    }

    @Test
    void shouldRejectUnknownCategory() {
        assertThrows(
            CategoryNotFoundException.class,
            () -> service.execute(new ChangeCategoryStatusCommand(UUID.randomUUID(), CategoryStatus.INACTIVE))
        );
    }
}