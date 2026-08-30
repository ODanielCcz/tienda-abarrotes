import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { SalesApi } from '../../data-access/sales-api';
import { SalesOverview } from './sales-overview';

describe('SalesOverview', () => {
  let fixture: ComponentFixture<SalesOverview>;
  const api = { list: vi.fn() };
  const context = {
    context: vi.fn(() => ({ warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({ warehouseId: 'warehouse-1', name: 'Principal' })),
  };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    api.list.mockReturnValue(of([order]));
    await TestBed.configureTestingModule({
      imports: [SalesOverview],
      providers: [
        provideRouter([]),
        { provide: SalesApi, useValue: api },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesOverview);
    fixture.detectChanges();
  });

  it('loads sales from the selected warehouse', () => {
    const component = fixture.componentInstance as unknown as { orders: () => unknown[] };

    expect(api.list).toHaveBeenCalledWith({ warehouseId: 'warehouse-1' });
    expect(component.orders()).toEqual([order]);
  });

  const order = {
    salesOrderId: 'order-1',
    orderNumber: 'SO-0001',
    branchId: 'branch-1',
    warehouseId: 'warehouse-1',
    customerId: null,
    deviceId: null,
    channel: 'POS',
    status: 'CONFIRMED',
    paymentStatus: 'PENDING',
    currencyCode: 'MXN',
    subtotal: 25.5,
    discountTotal: 0,
    taxTotal: 4.08,
    total: 29.58,
    idempotencyKey: 'key-1',
    createdAt: '2026-08-29T00:00:00Z',
    confirmedAt: '2026-08-29T00:00:00Z',
    cancelledAt: null,
    items: [],
  };
});
