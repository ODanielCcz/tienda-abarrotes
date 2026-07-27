package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeCustomerStatusRequest(
    @NotBlank
    String status
) {
}
