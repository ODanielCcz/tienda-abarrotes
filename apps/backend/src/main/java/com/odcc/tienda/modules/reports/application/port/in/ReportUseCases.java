package com.odcc.tienda.modules.reports.application.port.in;

import com.odcc.tienda.modules.reports.application.model.CashSummaryReport;
import com.odcc.tienda.modules.reports.application.model.CustomerSalesReport;
import com.odcc.tienda.modules.reports.application.model.ExpiringProductReport;
import com.odcc.tienda.modules.reports.application.model.GrossMarginReport;
import com.odcc.tienda.modules.reports.application.model.InventoryMovementReport;
import com.odcc.tienda.modules.reports.application.model.LowStockReport;
import com.odcc.tienda.modules.reports.application.model.ProductProfitabilityReport;
import com.odcc.tienda.modules.reports.application.model.ReturnsSummaryReport;
import com.odcc.tienda.modules.reports.application.model.SalesByPeriodReport;
import com.odcc.tienda.modules.reports.application.model.SalesSummaryReport;
import com.odcc.tienda.modules.reports.application.model.StockValuationReport;
import com.odcc.tienda.modules.reports.application.model.TopProductReport;
import com.odcc.tienda.modules.reports.application.query.ExpiringProductsQuery;
import com.odcc.tienda.modules.reports.application.query.PeriodReportQuery;
import com.odcc.tienda.modules.reports.application.query.ProductProfitabilityQuery;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
import com.odcc.tienda.modules.reports.application.query.StockValuationQuery;

import java.util.List;
import java.util.UUID;

public interface ReportUseCases {
    SalesSummaryReport salesSummary(ReportFilter filter, UUID actorUserId);

    List<TopProductReport> topProducts(ReportFilter filter, UUID actorUserId);

    List<CustomerSalesReport> customerSales(ReportFilter filter, UUID actorUserId);

    List<LowStockReport> lowStock(ReportFilter filter, UUID actorUserId);

    List<InventoryMovementReport> inventoryMovements(ReportFilter filter, UUID actorUserId);

    List<CashSummaryReport> cashSummary(ReportFilter filter, UUID actorUserId);

    List<SalesByPeriodReport> salesByPeriod(PeriodReportQuery query, UUID actorUserId);

    GrossMarginReport grossMargin(PeriodReportQuery query, UUID actorUserId);

    List<ProductProfitabilityReport> productProfitability(ProductProfitabilityQuery query, UUID actorUserId);

    StockValuationReport stockValuation(StockValuationQuery query, UUID actorUserId);

    List<ExpiringProductReport> expiringProducts(ExpiringProductsQuery query, UUID actorUserId);

    ReturnsSummaryReport returnsSummary(PeriodReportQuery query, UUID actorUserId);
}
