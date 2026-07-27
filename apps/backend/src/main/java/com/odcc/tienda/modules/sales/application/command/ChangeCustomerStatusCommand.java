package com.odcc.tienda.modules.sales.application.command;

import java.util.UUID;

public record ChangeCustomerStatusCommand(
    UUID customerId,
    String status
) {
}
