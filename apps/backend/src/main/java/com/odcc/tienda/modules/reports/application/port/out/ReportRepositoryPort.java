package com.odcc.tienda.modules.reports.application.port.out;

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

public interface ReportRepositoryPort {
    SalesSummaryReport salesSummary(ReportFilter filter);

    List<TopProductReport> topProducts(ReportFilter filter);

    List<CustomerSalesReport> customerSales(ReportFilter filter);

    List<LowStockReport> lowStock(ReportFilter filter);

    List<InventoryMovementReport> inventoryMovements(ReportFilter filter);

    List<CashSummaryReport> cashSummary(ReportFilter filter);

    List<SalesByPeriodReport> salesByPeriod(PeriodReportQuery query);

    GrossMarginReport grossMargin(PeriodReportQuery query);

    List<ProductProfitabilityReport> productProfitability(ProductProfitabilityQuery query);

    StockValuationReport stockValuation(StockValuationQuery query);

    List<ExpiringProductReport> expiringProducts(ExpiringProductsQuery query);

    ReturnsSummaryReport returnsSummary(PeriodReportQuery query);
}
