package com.odcc.tienda.modules.reports.application.port.out;

import com.odcc.tienda.modules.reports.application.model.CashSummaryReport;
import com.odcc.tienda.modules.reports.application.model.CustomerSalesReport;
import com.odcc.tienda.modules.reports.application.model.InventoryMovementReport;
import com.odcc.tienda.modules.reports.application.model.LowStockReport;
import com.odcc.tienda.modules.reports.application.model.SalesSummaryReport;
import com.odcc.tienda.modules.reports.application.model.TopProductReport;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;

import java.util.List;

public interface ReportRepositoryPort {
    SalesSummaryReport salesSummary(ReportFilter filter);

    List<TopProductReport> topProducts(ReportFilter filter);

    List<CustomerSalesReport> customerSales(ReportFilter filter);

    List<LowStockReport> lowStock(ReportFilter filter);

    List<InventoryMovementReport> inventoryMovements(ReportFilter filter);

    List<CashSummaryReport> cashSummary(ReportFilter filter);
}
