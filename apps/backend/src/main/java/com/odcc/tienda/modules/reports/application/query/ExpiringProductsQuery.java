package com.odcc.tienda.modules.reports.application.query;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringProductsQuery(
    UUID branchId,
    UUID warehouseId,
    Integer days,
    Integer limit,
    LocalDate asOf
) {
}
