package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.UpdateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryParentNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.exception.InvalidCategoryException;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
public final class UpdateCategoryService implements UpdateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Category execute(UpdateCategoryCommand command) {
        return transactionRunner.required(() -> update(command));
    }

    private Category update(UpdateCategoryCommand command) {
        Objects.requireNonNull(command, "El comando de actualizacion es obligatorio");
        Objects.requireNonNull(command.categoryId(), "El id de la categoria es obligatorio");

        if (command.categoryId().equals(command.parentCategoryId())) {
            throw new InvalidCategoryException("Una categoria no puede ser padre de si misma");
        }

        Category currentCategory = categoryRepository
            .findById(command.categoryId())
            .orElseThrow(() -> new CategoryNotFoundException(command.categoryId()));

        validateParentExists(command.parentCategoryId());
        validateParentDoesNotCreateCycle(command.categoryId(), command.parentCategoryId());

        Category updatedCategory = currentCategory.update(
            command.code(),
            command.name(),
            command.parentCategoryId()
        );

        boolean codeChanged = !currentCategory.getCode().equals(updatedCategory.getCode());
        if (codeChanged && categoryRepository.existsByCodeAndIdNot(updatedCategory.getCode(), updatedCategory.getId())) {
            throw new CategoryCodeAlreadyExistsException(updatedCategory.getCode());
        }

        Category savedCategory = categoryRepository.save(updatedCategory);
        auditPort.record(new BusinessAuditEvent(
            "CATEGORY_UPDATED",
            "CATEGORY",
            savedCategory.getId(),
            stateOf(currentCategory),
            stateOf(savedCategory),
            Map.of()
        ));
        return savedCategory;
    }

    private void validateParentExists(UUID parentCategoryId) {
        if (parentCategoryId != null && categoryRepository.findById(parentCategoryId).isEmpty()) {
            throw new CategoryParentNotFoundException(parentCategoryId);
        }
    }

    private void validateParentDoesNotCreateCycle(UUID categoryId, UUID parentCategoryId) {
        if (parentCategoryId != null && categoryRepository.hasAncestor(parentCategoryId, categoryId)) {
            throw new InvalidCategoryException("Una categoria no puede usar una subcategoria como padre");
        }
    }

    private static Map<String, Object> stateOf(Category category) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", category.getCode());
        state.put("name", category.getName());
        state.put("status", category.getStatus().name());
        if (category.getParentCategoryId() != null) {
            state.put("parentCategoryId", category.getParentCategoryId());
        }
        return state;
    }
}
