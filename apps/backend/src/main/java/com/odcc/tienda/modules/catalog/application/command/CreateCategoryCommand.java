package com.odcc.tienda.modules.catalog.application.command;

import java.util.UUID;

public record CreateCategoryCommand(
    String code,
    String name,
    UUID parentCategoryId
) {
}