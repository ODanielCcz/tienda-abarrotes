package com.odcc.tienda.modules.catalog.application.command;

public record CreateBrandCommand(
    String code,
    String name
) {
}
