package com.odcc.tienda.modules.reports.application.usecase;

import com.odcc.tienda.modules.reports.application.model.CashSummaryReport;
import com.odcc.tienda.modules.reports.application.model.CustomerSalesReport;
import com.odcc.tienda.modules.reports.application.model.InventoryMovementReport;
import com.odcc.tienda.modules.reports.application.model.LowStockReport;
import com.odcc.tienda.modules.reports.application.model.SalesSummaryReport;
import com.odcc.tienda.modules.reports.application.model.TopProductReport;
import com.odcc.tienda.modules.reports.application.port.in.ReportUseCases;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ReportService implements ReportUseCases {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final ReportRepositoryPort repository;

    @Override
    public SalesSummaryReport salesSummary(ReportFilter filter) {
        return repository.salesSummary(normalize(filter));
    }

    @Override
    public List<TopProductReport> topProducts(ReportFilter filter) {
        return repository.topProducts(normalize(filter));
    }

    @Override
    public List<CustomerSalesReport> customerSales(ReportFilter filter) {
        return repository.customerSales(normalize(filter));
    }

    @Override
    public List<LowStockReport> lowStock(ReportFilter filter) {
        return repository.lowStock(normalize(filter));
    }

    @Override
    public List<InventoryMovementReport> inventoryMovements(ReportFilter filter) {
        return repository.inventoryMovements(normalize(filter));
    }

    @Override
    public List<CashSummaryReport> cashSummary(ReportFilter filter) {
        return repository.cashSummary(normalize(filter));
    }

    private ReportFilter normalize(ReportFilter filter) {
        if (filter == null) return new ReportFilter(null, null, null, null, null, DEFAULT_LIMIT);
        int limit = filter.limit() == null ? DEFAULT_LIMIT : filter.limit();
        if (limit < 1) limit = DEFAULT_LIMIT;
        if (limit > MAX_LIMIT) limit = MAX_LIMIT;
        return new ReportFilter(filter.from(), filter.to(), filter.branchId(), filter.warehouseId(), filter.customerId(), limit);
    }
}
