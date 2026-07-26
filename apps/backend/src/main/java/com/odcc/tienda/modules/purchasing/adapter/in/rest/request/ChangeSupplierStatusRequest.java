package com.odcc.tienda.modules.purchasing.adapter.in.rest.request;

import jakarta.validation.constraints.NotBlank;

public record ChangeSupplierStatusRequest(@NotBlank String status) {
}
