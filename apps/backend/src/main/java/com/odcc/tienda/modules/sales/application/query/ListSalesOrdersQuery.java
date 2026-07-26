package com.odcc.tienda.modules.sales.application.query;

import java.util.UUID;

public record ListSalesOrdersQuery(UUID warehouseId, UUID customerId, String status) {
}
