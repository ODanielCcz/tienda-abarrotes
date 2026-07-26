package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryParentNotFoundException;
import com.odcc.tienda.modules.catalog.domain.exception.InvalidCategoryException;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.modules.catalog.support.InMemoryCategoryRepository;
import com.odcc.tienda.shared.support.ImmediateTransactionRunner;
import com.odcc.tienda.shared.support.InMemoryBusinessAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateCategoryServiceTest {

    private InMemoryCategoryRepository repository;
    private InMemoryBusinessAuditPort auditPort;
    private UpdateCategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        auditPort = new InMemoryBusinessAuditPort();
        service = new UpdateCategoryService(repository, new ImmediateTransactionRunner(), auditPort);
    }

    @Test
    void shouldUpdateCategoryAndAuditEvent() {
        Category category = repository.save(Category.create("OLD", "Anterior", null));
        Category parent = repository.save(Category.create("ROOT", "Raiz", null));

        Category updated = service.execute(new UpdateCategoryCommand(category.getId(), "NEW", "Nueva", parent.getId()));

        assertEquals("NEW", updated.getCode());
        assertEquals("Nueva", updated.getName());
        assertEquals(parent.getId(), updated.getParentCategoryId());
        assertEquals("CATEGORY_UPDATED", auditPort.events().getFirst().eventType());
    }

    @Test
    void shouldRejectDuplicatedCode() {
        repository.save(Category.create("EXISTING", "Existente", null));
        Category category = repository.save(Category.create("EDIT", "Editable", null));

        assertThrows(
            CategoryCodeAlreadyExistsException.class,
            () -> service.execute(new UpdateCategoryCommand(category.getId(), "EXISTING", "Duplicada", null))
        );
    }

    @Test
    void shouldRejectSelfParent() {
        Category category = repository.save(Category.create("SELF", "Self", null));

        assertThrows(
            InvalidCategoryException.class,
            () -> service.execute(new UpdateCategoryCommand(category.getId(), "SELF", "Self", category.getId()))
        );
    }

    @Test
    void shouldRejectIndirectCycle() {
        Category root = repository.save(Category.create("ROOT", "Raiz", null));
        Category child = repository.save(Category.create("CHILD", "Hija", root.getId()));

        assertThrows(
            InvalidCategoryException.class,
            () -> service.execute(new UpdateCategoryCommand(root.getId(), "ROOT", "Raiz", child.getId()))
        );
    }

    @Test
    void shouldRejectUnknownParent() {
        Category category = repository.save(Category.create("CHILD", "Child", null));

        assertThrows(
            CategoryParentNotFoundException.class,
            () -> service.execute(new UpdateCategoryCommand(category.getId(), "CHILD", "Child", UUID.randomUUID()))
        );
    }

    @Test
    void shouldRejectUnknownCategory() {
        assertThrows(
            CategoryNotFoundException.class,
            () -> service.execute(new UpdateCategoryCommand(UUID.randomUUID(), "MISS", "Missing", null))
        );
    }
}
