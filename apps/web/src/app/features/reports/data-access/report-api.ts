import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CashSummaryReport,
  CustomerSalesReport,
  ExpiringProductReport,
  ExpiringProductsFilters,
  GrossMarginReport,
  InventoryMovementReport,
  LowStockReport,
  PeriodReportFilters,
  ProductProfitabilityReport,
  ReportFilters,
  ReturnsSummaryReport,
  SalesByPeriodReport,
  SalesSummaryReport,
  StockValuationReport,
  TopProductReport,
} from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  salesSummary(filters: ReportFilters = {}): Observable<SalesSummaryReport> {
    return this.getOne('/reports/sales-summary', filters, 'resumen de ventas');
  }

  topProducts(filters: ReportFilters = {}): Observable<TopProductReport[]> {
    return this.getList('/reports/top-products', filters);
  }

  customerSales(filters: ReportFilters = {}): Observable<CustomerSalesReport[]> {
    return this.getList('/reports/customer-sales', filters);
  }

  lowStock(filters: ReportFilters = {}): Observable<LowStockReport[]> {
    return this.getList('/reports/low-stock', filters);
  }

  inventoryMovements(filters: ReportFilters = {}): Observable<InventoryMovementReport[]> {
    return this.getList('/reports/inventory-movements', filters);
  }

  cashSummary(filters: ReportFilters = {}): Observable<CashSummaryReport[]> {
    return this.getList('/reports/cash-summary', filters);
  }

  salesByPeriod(filters: PeriodReportFilters = {}): Observable<SalesByPeriodReport[]> {
    return this.getList('/reports/sales-by-period', filters, this.periodParams(filters));
  }

  grossMargin(filters: ReportFilters = {}): Observable<GrossMarginReport> {
    return this.getOne('/reports/gross-margin', filters, 'margen bruto');
  }

  productProfitability(filters: ReportFilters = {}): Observable<ProductProfitabilityReport[]> {
    return this.getList('/reports/product-profitability', filters);
  }

  stockValuation(filters: ReportFilters = {}): Observable<StockValuationReport> {
    return this.getOne('/reports/stock-valuation', filters, 'valorización de inventario');
  }

  expiringProducts(filters: ExpiringProductsFilters = {}): Observable<ExpiringProductReport[]> {
    let params = this.commonParams(filters);
    if (filters.days !== undefined) params = params.set('days', filters.days);
    return this.getList('/reports/expiring-products', filters, params);
  }

  returnsSummary(filters: PeriodReportFilters = {}): Observable<ReturnsSummaryReport> {
    return this.getOne(
      '/reports/returns-summary',
      filters,
      'resumen de devoluciones',
      this.periodParams(filters),
    );
  }

  private getList<T>(
    path: string,
    filters: ReportFilters,
    params = this.commonParams(filters),
  ): Observable<T[]> {
    return this.http
      .get<ApiResponse<T[]>>(`${this.config.baseUrl}${path}`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  private getOne<T>(
    path: string,
    filters: ReportFilters,
    resource: string,
    params = this.commonParams(filters),
  ): Observable<T> {
    return this.http.get<ApiResponse<T>>(`${this.config.baseUrl}${path}`, { params }).pipe(
      map((response) => {
        if (response.data === null) throw new Error(`La API respondió sin datos de ${resource}.`);
        return response.data;
      }),
    );
  }

  private periodParams(filters: PeriodReportFilters): HttpParams {
    let params = this.commonParams(filters);
    if (filters.groupBy) params = params.set('groupBy', filters.groupBy);
    return params;
  }

  private commonParams(filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.branchId) params = params.set('branchId', filters.branchId);
    if (filters.warehouseId) params = params.set('warehouseId', filters.warehouseId);
    if (filters.customerId) params = params.set('customerId', filters.customerId);
    if (filters.limit !== undefined) params = params.set('limit', filters.limit);
    return params;
  }
}
