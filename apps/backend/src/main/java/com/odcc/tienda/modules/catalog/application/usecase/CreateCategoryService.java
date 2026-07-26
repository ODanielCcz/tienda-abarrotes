package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.CreateCategoryCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryCodeAlreadyExistsException;
import com.odcc.tienda.modules.catalog.application.exception.CategoryParentNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.CreateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
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
public final class CreateCategoryService implements CreateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Category execute(CreateCategoryCommand command) {
        return transactionRunner.required(() -> create(command));
    }

    private Category create(CreateCategoryCommand command) {
        Objects.requireNonNull(command, "El comando de creacion es obligatorio");
        validateParentExists(command.parentCategoryId());

        Category category = Category.create(command.code(), command.name(), command.parentCategoryId());
        if (categoryRepository.existsByCode(category.getCode())) {
            throw new CategoryCodeAlreadyExistsException(category.getCode());
        }

        Category savedCategory = categoryRepository.save(category);
        auditPort.record(new BusinessAuditEvent(
            "CATEGORY_CREATED",
            "CATEGORY",
            savedCategory.getId(),
            Map.of(),
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