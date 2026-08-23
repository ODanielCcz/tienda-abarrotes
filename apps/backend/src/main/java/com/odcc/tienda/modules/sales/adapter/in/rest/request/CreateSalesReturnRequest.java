package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSalesReturnRequest(
    @NotBlank @Size(max = 1000) String reason,
    @NotEmpty @Size(max = 200) List<@Valid CreateSalesReturnItemRequest> items
) {
}