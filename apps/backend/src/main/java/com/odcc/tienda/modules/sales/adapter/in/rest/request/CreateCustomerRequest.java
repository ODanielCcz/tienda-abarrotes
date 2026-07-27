package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
    @Size(max = 50)
    String customerCode,

    String customerType,

    @NotBlank
    @Size(max = 200)
    String displayName,

    @Email
    @Size(max = 254)
    String email,

    @Size(max = 40)
    String phone
) {
}
