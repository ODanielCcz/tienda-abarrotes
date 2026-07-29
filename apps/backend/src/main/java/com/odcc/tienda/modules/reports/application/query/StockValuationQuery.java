package com.odcc.tienda.modules.reports.application.query;

import java.util.UUID;

public record StockValuationQuery(
    UUID branchId,
    UUID warehouseId,
    Integer limit
) {
}
