package com.odcc.tienda.modules.reports.application.query;

import java.time.LocalDate;
import java.util.UUID;

public record ProductProfitabilityQuery(
    LocalDate from,
    LocalDate to,
    UUID branchId,
    UUID warehouseId,
    UUID customerId,
    Integer limit
) {
}
