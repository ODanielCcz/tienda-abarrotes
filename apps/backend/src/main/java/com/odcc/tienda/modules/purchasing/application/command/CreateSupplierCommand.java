package com.odcc.tienda.modules.purchasing.application.command;

public record CreateSupplierCommand(
    String supplierCode,
    String legalName,
    String tradeName,
    String taxId,
    String email,
    String phone,
    Integer creditDays
) {
}
