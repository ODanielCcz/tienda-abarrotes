package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupplierRequest(
    @NotBlank @Size(max = 50) String supplierCode,
    @NotBlank @Size(max = 200) String legalName,
    @Size(max = 200) String tradeName,
    @Size(max = 20) String taxId,
    @Size(max = 254) String email,
    @Size(max = 40) String phone,
    @Min(0) Integer creditDays
) {
}
