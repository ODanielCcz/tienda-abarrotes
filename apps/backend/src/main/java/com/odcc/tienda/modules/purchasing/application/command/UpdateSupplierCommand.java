package com.odcc.tienda.modules.purchasing.application.command;

import java.util.UUID;

public record UpdateSupplierCommand(
    UUID supplierId,
    String supplierCode,
    String legalName,
    String tradeName,
    String taxId,
    String email,
    String phone,
    Integer creditDays
) {
}
