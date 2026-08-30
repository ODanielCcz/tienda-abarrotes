import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { ReportApi } from '../../reports/data-access/report-api';
import { DashboardApi } from './dashboard-api';

describe('DashboardApi', () => {
  const reports = {
    salesSummary: vi.fn(() => of({ total: 100 })),
    grossMargin: vi.fn(() => of({ grossProfit: 40 })),
    lowStock: vi.fn(() => of([])),
    cashSummary: vi.fn(() => of([])),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [{ provide: ReportApi, useValue: reports }] });
  });

  it('does not call report endpoints that the user cannot access', () => {
    const api = TestBed.inject(DashboardApi);

    api
      .load({
        includeSales: true,
        includeMargin: false,
        includeInventory: false,
        includeCash: false,
      })
      .subscribe((snapshot) => expect(snapshot.sales).toEqual({ total: 100 }));

    expect(reports.salesSummary).toHaveBeenCalled();
    expect(reports.grossMargin).not.toHaveBeenCalled();
    expect(reports.lowStock).not.toHaveBeenCalled();
    expect(reports.cashSummary).not.toHaveBeenCalled();
  });
});
