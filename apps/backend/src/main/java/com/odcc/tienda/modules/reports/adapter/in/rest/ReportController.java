package com.odcc.tienda.modules.reports.adapter.in.rest;

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
import com.odcc.tienda.modules.reports.application.query.ExpiringProductsQuery;
import com.odcc.tienda.modules.reports.application.query.PeriodReportQuery;
import com.odcc.tienda.modules.reports.application.query.ProductProfitabilityQuery;
import com.odcc.tienda.modules.reports.application.query.ReportFilter;
import com.odcc.tienda.modules.reports.application.query.ReportGroupBy;
import com.odcc.tienda.modules.reports.application.query.StockValuationQuery;
import com.odcc.tienda.shared.web.response.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes", description = "Reportes operativos de ventas, inventario, devoluciones y caja")
public class ReportController {

    private final ReportUseCases reports;

    @GetMapping("/sales-summary")
    @Operation(summary = "Resumen de ventas")
    @PreAuthorize("hasAuthority('REPORT_SALES_READ')")
    public ResponseEntity<ApiResponseDto<SalesSummaryReport>> salesSummary(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_SALES_SUMMARY_FOUND",
            "Resumen de ventas consultado correctamente",
            reports.salesSummary(filter(from, to, branchId, warehouseId, customerId, null)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/top-products")
    @Operation(summary = "Productos mas vendidos")
    @PreAuthorize("hasAuthority('REPORT_SALES_READ')")
    public ResponseEntity<ApiResponseDto<List<TopProductReport>>> topProducts(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_TOP_PRODUCTS_FOUND",
            "Productos mas vendidos consultados correctamente",
            reports.topProducts(filter(from, to, branchId, warehouseId, customerId, limit)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/customer-sales")
    @Operation(summary = "Ventas por cliente")
    @PreAuthorize("hasAuthority('REPORT_SALES_READ')")
    public ResponseEntity<ApiResponseDto<List<CustomerSalesReport>>> customerSales(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_CUSTOMER_SALES_FOUND",
            "Ventas por cliente consultadas correctamente",
            reports.customerSales(filter(from, to, branchId, warehouseId, customerId, limit)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Stock bajo")
    @PreAuthorize("hasAuthority('REPORT_INVENTORY_READ')")
    public ResponseEntity<ApiResponseDto<List<LowStockReport>>> lowStock(
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_LOW_STOCK_FOUND",
            "Stock bajo consultado correctamente",
            reports.lowStock(filter(null, null, branchId, warehouseId, null, limit)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/inventory-movements")
    @Operation(summary = "Resumen de movimientos de inventario")
    @PreAuthorize("hasAuthority('REPORT_INVENTORY_READ')")
    public ResponseEntity<ApiResponseDto<List<InventoryMovementReport>>> inventoryMovements(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_INVENTORY_MOVEMENTS_FOUND",
            "Movimientos de inventario consultados correctamente",
            reports.inventoryMovements(filter(from, to, branchId, warehouseId, null, limit)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/cash-summary")
    @Operation(summary = "Resumen de caja")
    @PreAuthorize("hasAuthority('REPORT_CASH_READ')")
    public ResponseEntity<ApiResponseDto<List<CashSummaryReport>>> cashSummary(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_CASH_SUMMARY_FOUND",
            "Resumen de caja consultado correctamente",
            reports.cashSummary(filter(from, to, branchId, null, null, limit)),
            request.getRequestURI()
        ));
    }

    @GetMapping("/sales-by-period")
    @Operation(summary = "Ventas brutas, devoluciones y ventas netas por periodo")
    @PreAuthorize("hasAuthority('REPORT_SALES_BY_PERIOD_READ')")
    public ResponseEntity<ApiResponseDto<List<SalesByPeriodReport>>> salesByPeriod(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) String groupBy,
        HttpServletRequest request
    ) {
        List<SalesByPeriodReport> result = reports.salesByPeriod(periodQuery(
            from, to, branchId, warehouseId, customerId, groupBy
        ));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_SALES_BY_PERIOD_FOUND",
            "Ventas por periodo consultadas correctamente",
            result,
            request.getRequestURI()
        ));
    }

    @GetMapping("/gross-margin")
    @Operation(summary = "Margen bruto neto de devoluciones")
    @PreAuthorize("hasAuthority('REPORT_GROSS_MARGIN_READ')")
    public ResponseEntity<ApiResponseDto<GrossMarginReport>> grossMargin(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        HttpServletRequest request
    ) {
        GrossMarginReport result = reports.grossMargin(periodQuery(
            from, to, branchId, warehouseId, customerId, null
        ));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_GROSS_MARGIN_FOUND",
            "Margen bruto consultado correctamente",
            result,
            request.getRequestURI()
        ));
    }

    @GetMapping("/product-profitability")
    @Operation(summary = "Rentabilidad por producto y presentacion")
    @PreAuthorize("hasAuthority('REPORT_GROSS_MARGIN_READ')")
    public ResponseEntity<ApiResponseDto<List<ProductProfitabilityReport>>> productProfitability(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        List<ProductProfitabilityReport> result = reports.productProfitability(
            new ProductProfitabilityQuery(from, to, branchId, warehouseId, customerId, limit)
        );
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_PRODUCT_PROFITABILITY_FOUND",
            "Rentabilidad por producto consultada correctamente",
            result,
            request.getRequestURI()
        ));
    }

    @GetMapping("/stock-valuation")
    @Operation(summary = "Valorizacion del inventario")
    @PreAuthorize("hasAuthority('REPORT_STOCK_VALUATION_READ')")
    public ResponseEntity<ApiResponseDto<StockValuationReport>> stockValuation(
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        StockValuationReport result = reports.stockValuation(
            new StockValuationQuery(branchId, warehouseId, limit)
        );
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_STOCK_VALUATION_FOUND",
            "Valorizacion de inventario consultada correctamente",
            result,
            request.getRequestURI()
        ));
    }

    @GetMapping("/expiring-products")
    @Operation(summary = "Productos con lotes proximos a caducar")
    @PreAuthorize("hasAuthority('REPORT_EXPIRING_PRODUCTS_READ')")
    public ResponseEntity<ApiResponseDto<List<ExpiringProductReport>>> expiringProducts(
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) Integer days,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        List<ExpiringProductReport> result = reports.expiringProducts(
            new ExpiringProductsQuery(branchId, warehouseId, days, limit, null)
        );
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_EXPIRING_PRODUCTS_FOUND",
            "Productos proximos a caducar consultados correctamente",
            result,
            request.getRequestURI()
        ));
    }

    @GetMapping("/returns-summary")
    @Operation(summary = "Resumen de devoluciones confirmadas")
    @PreAuthorize("hasAuthority('REPORT_RETURNS_READ')")
    public ResponseEntity<ApiResponseDto<ReturnsSummaryReport>> returnsSummary(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) String groupBy,
        HttpServletRequest request
    ) {
        ReturnsSummaryReport result = reports.returnsSummary(periodQuery(
            from, to, branchId, warehouseId, customerId, groupBy
        ));
        return ResponseEntity.ok(ApiResponseDto.success(
            HttpStatus.OK,
            "REPORT_RETURNS_SUMMARY_FOUND",
            "Resumen de devoluciones consultado correctamente",
            result,
            request.getRequestURI()
        ));
    }

    private ReportFilter filter(
        LocalDate from,
        LocalDate to,
        UUID branchId,
        UUID warehouseId,
        UUID customerId,
        Integer limit
    ) {
        return new ReportFilter(from, to, branchId, warehouseId, customerId, limit);
    }

    private PeriodReportQuery periodQuery(
        LocalDate from,
        LocalDate to,
        UUID branchId,
        UUID warehouseId,
        UUID customerId,
        String groupBy
    ) {
        return new PeriodReportQuery(
            from,
            to,
            branchId,
            warehouseId,
            customerId,
            ReportGroupBy.from(groupBy)
        );
    }
}
