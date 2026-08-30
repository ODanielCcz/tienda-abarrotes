import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { finalize, forkJoin, of } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { MetricCard } from '../../../dashboard/components/metric-card/metric-card';
import {
  PeriodFilter,
  PeriodSelection,
} from '../../../dashboard/components/period-filter/period-filter';
import { CustomerApi } from '../../../customers/data-access/customer-api';
import { Customer } from '../../../customers/models/customer.model';
import { ReportApi } from '../../data-access/report-api';
import {
  CashSummaryReport,
  CustomerSalesReport,
  ExpiringProductReport,
  GrossMarginReport,
  InventoryMovementReport,
  LowStockReport,
  ProductProfitabilityReport,
  ReportFilters,
  ReportGroupBy,
  ReturnsSummaryReport,
  SalesByPeriodReport,
  SalesSummaryReport,
  StockValuationReport,
  TopProductReport,
} from '../../models/report.model';

interface ReportCenterData {
  salesSummary: SalesSummaryReport | null;
  topProducts: TopProductReport[];
  customerSales: CustomerSalesReport[];
  lowStock: LowStockReport[];
  inventoryMovements: InventoryMovementReport[];
  cashSummary: CashSummaryReport[];
  salesByPeriod: SalesByPeriodReport[];
  grossMargin: GrossMarginReport | null;
  profitability: ProductProfitabilityReport[];
  stockValuation: StockValuationReport | null;
  expiringProducts: ExpiringProductReport[];
  returnsSummary: ReturnsSummaryReport | null;
  customers: Customer[];
}

@Component({
  selector: 'app-report-center',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTabsModule,
    MetricCard,
    PeriodFilter,
  ],
  templateUrl: './report-center.html',
  styleUrl: './report-center.scss',
})
export class ReportCenter {
  private readonly api = inject(ReportApi);
  private readonly customerApi = inject(CustomerApi);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly canSales = this.sessionStore.hasPermission('REPORT_SALES_READ');
  protected readonly canPeriod = this.sessionStore.hasPermission('REPORT_SALES_BY_PERIOD_READ');
  protected readonly canMargin = this.sessionStore.hasPermission('REPORT_GROSS_MARGIN_READ');
  protected readonly canInventory = this.sessionStore.hasPermission('REPORT_INVENTORY_READ');
  protected readonly canValuation = this.sessionStore.hasPermission('REPORT_STOCK_VALUATION_READ');
  protected readonly canExpiring = this.sessionStore.hasPermission('REPORT_EXPIRING_PRODUCTS_READ');
  protected readonly canReturns = this.sessionStore.hasPermission('REPORT_RETURNS_READ');
  protected readonly canCash = this.sessionStore.hasPermission('REPORT_CASH_READ');
  protected readonly canReadCustomers = this.sessionStore.hasPermission('SALES_CUSTOMER_READ');
  protected readonly from = signal(this.dateDaysAgo(29));
  protected readonly to = signal(this.dateDaysAgo(0));
  protected readonly data = signal<ReportCenterData>(this.emptyData());
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly filtersForm = this.fb.nonNullable.group({
    customerId: [''],
    groupBy: ['DAY' as ReportGroupBy],
    limit: [10],
    days: [30],
  });

  constructor() {
    this.load();
  }

  protected applyPeriod(period: PeriodSelection): void {
    this.from.set(period.from);
    this.to.set(period.to);
    this.load();
  }

  protected refresh(): void {
    this.load();
  }

  private load(): void {
    const context = this.contextStore.context();
    const controls = this.filtersForm.getRawValue();
    const filters: ReportFilters = {
      from: this.from(),
      to: this.to(),
      ...(context.branchId ? { branchId: context.branchId } : {}),
      ...(context.warehouseId ? { warehouseId: context.warehouseId } : {}),
      ...(controls.customerId ? { customerId: controls.customerId } : {}),
      limit: Math.min(100, Math.max(1, Number(controls.limit) || 10)),
    };
    const periodFilters = { ...filters, groupBy: controls.groupBy };
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      salesSummary: this.canSales ? this.api.salesSummary(filters) : of(null),
      topProducts: this.canSales ? this.api.topProducts(filters) : of([]),
      customerSales: this.canSales ? this.api.customerSales(filters) : of([]),
      lowStock: this.canInventory ? this.api.lowStock(filters) : of([]),
      inventoryMovements: this.canInventory ? this.api.inventoryMovements(filters) : of([]),
      cashSummary: this.canCash ? this.api.cashSummary(filters) : of([]),
      salesByPeriod: this.canPeriod ? this.api.salesByPeriod(periodFilters) : of([]),
      grossMargin: this.canMargin ? this.api.grossMargin(filters) : of(null),
      profitability: this.canMargin ? this.api.productProfitability(filters) : of([]),
      stockValuation: this.canValuation ? this.api.stockValuation(filters) : of(null),
      expiringProducts: this.canExpiring
        ? this.api.expiringProducts({ ...filters, days: Number(controls.days) || 30 })
        : of([]),
      returnsSummary: this.canReturns ? this.api.returnsSummary(periodFilters) : of(null),
      customers: this.canReadCustomers
        ? this.customerApi.list({ status: 'ACTIVE' })
        : of([] as Customer[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => this.data.set(data),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar los reportes.')),
      });
  }

  private emptyData(): ReportCenterData {
    return {
      salesSummary: null,
      topProducts: [],
      customerSales: [],
      lowStock: [],
      inventoryMovements: [],
      cashSummary: [],
      salesByPeriod: [],
      grossMargin: null,
      profitability: [],
      stockValuation: null,
      expiringProducts: [],
      returnsSummary: null,
      customers: [],
    };
  }

  private dateDaysAgo(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString().slice(0, 10);
  }
}
