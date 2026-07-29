package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.List;

public record StockValuationReport(
    BigDecimal totalOnHandQuantity,
    BigDecimal totalStockValue,
    List<StockValuationItem> items
) {
}
