package com.odcc.tienda.modules.catalog.application.command;

import com.odcc.tienda.modules.catalog.domain.model.CategoryStatus;

import java.util.UUID;

public record ChangeCategoryStatusCommand(
    UUID categoryId,
    CategoryStatus status
) {
}