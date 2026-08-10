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
import com.odcc.tienda.shared.application.authorization.BranchScope;

import java.util.List;

public interface ReportRepositoryPort {
    SalesSummaryReport salesSummary(ReportFilter filter, BranchScope scope);

    List<TopProductReport> topProducts(ReportFilter filter, BranchScope scope);

    List<CustomerSalesReport> customerSales(ReportFilter filter, BranchScope scope);

    List<LowStockReport> lowStock(ReportFilter filter, BranchScope scope);

    List<InventoryMovementReport> inventoryMovements(ReportFilter filter, BranchScope scope);

    List<CashSummaryReport> cashSummary(ReportFilter filter, BranchScope scope);

    List<SalesByPeriodReport> salesByPeriod(PeriodReportQuery query, BranchScope scope);

    GrossMarginReport grossMargin(PeriodReportQuery query, BranchScope scope);

    List<ProductProfitabilityReport> productProfitability(ProductProfitabilityQuery query, BranchScope scope);

    StockValuationReport stockValuation(StockValuationQuery query, BranchScope scope);

    List<ExpiringProductReport> expiringProducts(ExpiringProductsQuery query, BranchScope scope);

    ReturnsSummaryReport returnsSummary(PeriodReportQuery query, BranchScope scope);
}
