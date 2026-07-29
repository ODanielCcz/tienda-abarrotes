package com.odcc.tienda.modules.reports.application.usecase;

import com.odcc.tienda.modules.reports.application.exception.InvalidReportFilterException;
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
import com.odcc.tienda.modules.reports.application.port.in.ReportUseCases;
import com.odcc.tienda.modules.reports.application.port.out.ReportRepositoryPort;
import com.odcc.tienda.modules.reports.application.query.ExpiringProductsQuery;
import com.odcc.tienda.modules.reports.application.query.PeriodReportQuery;
import com.odcc.tienda.modules.reports.application.query.ProductProfitabilityQuery;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
import com.odcc.tienda.modules.reports.application.query.ReportGroupBy;
import com.odcc.tienda.modules.reports.application.query.StockValuationQuery;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiredArgsConstructor
public class ReportService implements ReportUseCases {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int DEFAULT_EXPIRING_DAYS = 30;
    private static final int MAX_EXPIRING_DAYS = 365;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");

    private final ReportRepositoryPort repository;
    private final Clock clock;

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

    @Override
    public List<SalesByPeriodReport> salesByPeriod(PeriodReportQuery query) {
        return repository.salesByPeriod(normalize(query));
    }

    @Override
    public GrossMarginReport grossMargin(PeriodReportQuery query) {
        return repository.grossMargin(normalize(query));
    }

    @Override
    public List<ProductProfitabilityReport> productProfitability(ProductProfitabilityQuery query) {
        ProductProfitabilityQuery source = query == null
            ? new ProductProfitabilityQuery(null, null, null, null, null, null)
            : query;
        DateRange range = normalizeDates(source.from(), source.to());
        return repository.productProfitability(new ProductProfitabilityQuery(
            range.from(),
            range.to(),
            source.branchId(),
            source.warehouseId(),
            source.customerId(),
            normalizeLimit(source.limit())
        ));
    }

    @Override
    public StockValuationReport stockValuation(StockValuationQuery query) {
        StockValuationQuery source = query == null
            ? new StockValuationQuery(null, null, null)
            : query;
        return repository.stockValuation(new StockValuationQuery(
            source.branchId(),
            source.warehouseId(),
            normalizeLimit(source.limit())
        ));
    }

    @Override
    public List<ExpiringProductReport> expiringProducts(ExpiringProductsQuery query) {
        ExpiringProductsQuery source = query == null
            ? new ExpiringProductsQuery(null, null, null, null, null)
            : query;
        int days = source.days() == null ? DEFAULT_EXPIRING_DAYS : source.days();
        if (days < 1 || days > MAX_EXPIRING_DAYS) {
            throw new InvalidReportFilterException("days debe estar entre 1 y 365");
        }
        LocalDate asOf = source.asOf() == null ? today() : source.asOf();
        return repository.expiringProducts(new ExpiringProductsQuery(
            source.branchId(),
            source.warehouseId(),
            days,
            normalizeLimit(source.limit()),
            asOf
        ));
    }

    @Override
    public ReturnsSummaryReport returnsSummary(PeriodReportQuery query) {
        return repository.returnsSummary(normalize(query));
    }

    private ReportFilter normalize(ReportFilter filter) {
        ReportFilter source = filter == null
            ? new ReportFilter(null, null, null, null, null, null)
            : filter;
        DateRange range = normalizeDates(source.from(), source.to());
        return new ReportFilter(
            range.from(),
            range.to(),
            source.branchId(),
            source.warehouseId(),
            source.customerId(),
            normalizeLimit(source.limit())
        );
    }

    private PeriodReportQuery normalize(PeriodReportQuery query) {
        PeriodReportQuery source = query == null
            ? new PeriodReportQuery(null, null, null, null, null, ReportGroupBy.DAY)
            : query;
        DateRange range = normalizeDates(source.from(), source.to());
        return new PeriodReportQuery(
            range.from(),
            range.to(),
            source.branchId(),
            source.warehouseId(),
            source.customerId(),
            source.groupBy() == null ? ReportGroupBy.DAY : source.groupBy()
        );
    }

    private DateRange normalizeDates(LocalDate from, LocalDate to) {
        LocalDate normalizedTo = to == null ? today() : to;
        LocalDate normalizedFrom = from == null
            ? normalizedTo.minusDays(DEFAULT_RANGE_DAYS - 1L)
            : from;

        if (normalizedFrom.isAfter(normalizedTo)) {
            throw new InvalidReportFilterException("from no puede ser posterior a to");
        }

        long rangeDays = ChronoUnit.DAYS.between(normalizedFrom, normalizedTo) + 1;
        if (rangeDays > MAX_RANGE_DAYS) {
            throw new InvalidReportFilterException("El rango de fechas no puede superar 366 dias");
        }
        return new DateRange(normalizedFrom, normalizedTo);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DEFAULT_ZONE));
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
