package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryParentNotFoundException;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateCategoryServiceTest {

    private InMemoryCategoryRepository repository;
    private InMemoryBusinessAuditPort auditPort;
    private CreateCategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new CreateCategoryService(repository, new ImmediateTransactionRunner(), auditPort);
    }

    @Test
    void shouldCreateActiveCategoryAndAuditEvent() {
        Category category = service.execute(new CreateCategoryCommand("bebidas", "Bebidas", null));

        assertNotNull(category.getId());
        assertEquals("BEBIDAS", category.getCode());
        assertEquals(CategoryStatus.ACTIVE, category.getStatus());
        assertEquals("CATEGORY_CREATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldCreateCategoryWithExistingParent() {
        Category parent = repository.save(Category.create("PADRE", "Padre", null));

        Category child = service.execute(new CreateCategoryCommand("HIJO", "Hijo", parent.getId()));

        assertEquals(parent.getId(), child.getParentCategoryId());
    }

    @Test
    void shouldRejectDuplicatedCategoryCode() {
        service.execute(new CreateCategoryCommand("BEBIDAS", "Bebidas", null));

        assertThrows(
            CategoryCodeAlreadyExistsException.class,
            () -> service.execute(new CreateCategoryCommand("bebidas", "Otra", null))
        );
    }

    @Test
    void shouldRejectUnknownParentCategory() {
        UUID missingParentId = UUID.randomUUID();

        assertThrows(
            CategoryParentNotFoundException.class,
            () -> service.execute(new CreateCategoryCommand("HIJO", "Hijo", missingParentId))
        );
    }
}