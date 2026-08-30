import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { DashboardApi } from '../../data-access/dashboard-api';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  const api = { load: vi.fn() };
  const session = { hasPermission: vi.fn(() => true) };
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
    selectedBranch: vi.fn(() => ({ name: 'Sucursal centro' })),
    selectedWarehouse: vi.fn(() => ({ name: 'Almacén principal' })),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.load.mockReturnValue(
      of({
        sales: {
          ticketCount: 4,
          subtotal: 1000,
          discountTotal: 0,
          taxTotal: 160,
          total: 1160,
          averageTicket: 290,
        },
        margin: {
          grossRevenueExcludingTax: 1000,
          returnedRevenueExcludingTax: 0,
          netRevenueExcludingTax: 1000,
          grossCost: 600,
          returnedCost: 0,
          netCost: 600,
          grossProfit: 400,
          grossMarginPercent: 40,
        },
        lowStock: [{ productPresentationId: 'presentation-1' }],
        cashSessions: [{ status: 'OPEN', expectedAmount: 1500 }],
      }),
    );
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: DashboardApi, useValue: api },
        { provide: AuthSessionStore, useValue: session },
        { provide: OperationalContextStore, useValue: context },
      ],
    }).compileComponents();
  });

  it('loads authorized metrics using the operational context', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as { snapshot: () => any };

    expect(api.load).toHaveBeenCalledWith(
      expect.objectContaining({
        branchId: 'branch-1',
        warehouseId: 'warehouse-1',
        includeSales: true,
        includeMargin: true,
        includeInventory: true,
        includeCash: true,
      }),
    );
    expect(component.snapshot().sales.total).toBe(1160);
  });
});
