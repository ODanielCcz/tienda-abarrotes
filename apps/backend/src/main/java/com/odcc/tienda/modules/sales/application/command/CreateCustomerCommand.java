package com.odcc.tienda.modules.sales.application.command;

public record CreateCustomerCommand(
    String customerCode,
    String customerType,
    String displayName,
    String email,
    String phone
) {
}
