package com.odcc.tienda.modules.cash.application.query;

import java.util.UUID;

public record ListCashSessionsQuery(
    UUID cashRegisterId,
    String status
) {
}
