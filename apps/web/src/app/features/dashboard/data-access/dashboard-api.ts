import { inject, Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';

import { ReportApi } from '../../reports/data-access/report-api';
import {
  CashSummaryReport,
  GrossMarginReport,
  LowStockReport,
  ReportFilters,
  SalesSummaryReport,
} from '../../reports/models/report.model';

export interface DashboardQuery extends ReportFilters {
  includeSales: boolean;
  includeMargin: boolean;
  includeInventory: boolean;
  includeCash: boolean;
}

export interface DashboardSnapshot {
  sales: SalesSummaryReport | null;
  margin: GrossMarginReport | null;
  lowStock: LowStockReport[];
  cashSessions: CashSummaryReport[];
}

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly reports = inject(ReportApi);

  load(query: DashboardQuery): Observable<DashboardSnapshot> {
    const filters: ReportFilters = {
      ...(query.from ? { from: query.from } : {}),
      ...(query.to ? { to: query.to } : {}),
      ...(query.branchId ? { branchId: query.branchId } : {}),
      ...(query.warehouseId ? { warehouseId: query.warehouseId } : {}),
      limit: 10,
    };
    return forkJoin({
      sales: query.includeSales ? this.reports.salesSummary(filters) : of(null),
      margin: query.includeMargin ? this.reports.grossMargin(filters) : of(null),
      lowStock: query.includeInventory ? this.reports.lowStock(filters) : of([]),
      cashSessions: query.includeCash ? this.reports.cashSummary(filters) : of([]),
    });
  }
}
