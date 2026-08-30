import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { CustomerApi } from '../../../customers/data-access/customer-api';
import { ReportApi } from '../../data-access/report-api';
import { ReportCenter } from './report-center';

describe('ReportCenter', () => {
  let fixture: ComponentFixture<ReportCenter>;
  const api = {
    salesSummary: vi.fn(),
    topProducts: vi.fn(),
    customerSales: vi.fn(),
    lowStock: vi.fn(),
    inventoryMovements: vi.fn(),
    cashSummary: vi.fn(),
    salesByPeriod: vi.fn(),
    grossMargin: vi.fn(),
    productProfitability: vi.fn(),
    stockValuation: vi.fn(),
    expiringProducts: vi.fn(),
    returnsSummary: vi.fn(),
  };
  const customerApi = { list: vi.fn(() => of([])) };
  const session = { hasPermission: vi.fn(() => true) };
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.salesSummary.mockReturnValue(of({ ticketCount: 2, total: 500 }));
    api.topProducts.mockReturnValue(of([]));
    api.customerSales.mockReturnValue(of([]));
    api.lowStock.mockReturnValue(of([]));
    api.inventoryMovements.mockReturnValue(of([]));
    api.cashSummary.mockReturnValue(of([]));
    api.salesByPeriod.mockReturnValue(of([]));
    api.grossMargin.mockReturnValue(of({ grossProfit: 200, grossMarginPercent: 40 }));
    api.productProfitability.mockReturnValue(of([]));
    api.stockValuation.mockReturnValue(
      of({ totalOnHandQuantity: 10, totalStockValue: 100, items: [] }),
    );
    api.expiringProducts.mockReturnValue(of([]));
    api.returnsSummary.mockReturnValue(
      of({ returnCount: 0, returnedQuantity: 0, returnedAmount: 0, periods: [] }),
    );
    await TestBed.configureTestingModule({
      imports: [ReportCenter],
      providers: [
        { provide: ReportApi, useValue: api },
        { provide: CustomerApi, useValue: customerApi },
        { provide: AuthSessionStore, useValue: session },
        { provide: OperationalContextStore, useValue: context },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ReportCenter);
    fixture.detectChanges();
  });

  it('loads every authorized report with branch and warehouse filters', () => {
    const component = fixture.componentInstance as unknown as { data: () => any };
    expect(api.salesSummary).toHaveBeenCalledWith(
      expect.objectContaining({ branchId: 'branch-1', warehouseId: 'warehouse-1' }),
    );
    expect(api.salesByPeriod).toHaveBeenCalledWith(expect.objectContaining({ groupBy: 'DAY' }));
    expect(api.stockValuation).toHaveBeenCalledWith(
      expect.objectContaining({ warehouseId: 'warehouse-1', limit: 10 }),
    );
    expect(component.data().salesSummary.total).toBe(500);
  });
});
