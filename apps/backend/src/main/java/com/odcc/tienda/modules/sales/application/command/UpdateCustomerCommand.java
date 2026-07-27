package com.odcc.tienda.modules.sales.application.command;

import java.util.UUID;

public record UpdateCustomerCommand(
    UUID customerId,
    String customerCode,
    String customerType,
    String displayName,
    String email,
    String phone
) {
}
