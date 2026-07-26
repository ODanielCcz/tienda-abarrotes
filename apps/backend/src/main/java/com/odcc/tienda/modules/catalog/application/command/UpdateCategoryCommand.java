package com.odcc.tienda.modules.catalog.application.command;

import java.util.UUID;

public record UpdateCategoryCommand(
    UUID categoryId,
    String code,
    String name,
    UUID parentCategoryId
) {
}