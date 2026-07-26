package com.odcc.tienda.modules.catalog.application.usecase;

import com.odcc.tienda.modules.catalog.application.command.ChangeCategoryStatusCommand;
import com.odcc.tienda.modules.catalog.application.exception.CategoryNotFoundException;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeCategoryStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.domain.model.Category;
import com.odcc.tienda.shared.application.audit.BusinessAuditEvent;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public final class ChangeCategoryStatusService implements ChangeCategoryStatusUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final TransactionRunner transactionRunner;
    private final BusinessAuditPort auditPort;

    @Override
    public Category execute(ChangeCategoryStatusCommand command) {
        return transactionRunner.required(() -> changeStatus(command));
    }

    private Category changeStatus(ChangeCategoryStatusCommand command) {
        Objects.requireNonNull(command, "El comando de estado es obligatorio");
        Objects.requireNonNull(command.categoryId(), "El id de la categoria es obligatorio");
        Objects.requireNonNull(command.status(), "El estado de la categoria es obligatorio");

        Category category = categoryRepository
            .findById(command.categoryId())
            .orElseThrow(() -> new CategoryNotFoundException(command.categoryId()));

        Category changedCategory = category.changeStatus(command.status());
        if (changedCategory == category) {
            return category;
        }

        Category savedCategory = categoryRepository.save(changedCategory);
        auditPort.record(new BusinessAuditEvent(
            "CATEGORY_STATUS_CHANGED",
            "CATEGORY",
            savedCategory.getId(),
            Map.of("status", category.getStatus().name()),
            Map.of("status", savedCategory.getStatus().name()),
            Map.of()
        ));
        return savedCategory;
    }
}