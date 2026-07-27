package com.odcc.tienda.modules.sales.adapter.in.rest.request;

import java.util.UUID;

public record ConfirmSalesReturnRequest(
    UUID cashSessionId
) {
}