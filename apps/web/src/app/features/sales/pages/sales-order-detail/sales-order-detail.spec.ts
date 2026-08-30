import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { SalesApi } from '../../data-access/sales-api';
import { SalesOrderDetail } from './sales-order-detail';

describe('SalesOrderDetail', () => {
  let fixture: ComponentFixture<SalesOrderDetail>;
  const api = { get: vi.fn(), cancel: vi.fn() };
  const dialog = { open: vi.fn() };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.get.mockReturnValue(of(order));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    api.cancel.mockReturnValue(of({ ...order, status: 'CANCELLED' }));
    await TestBed.configureTestingModule({
      imports: [SalesOrderDetail],
      providers: [
        provideRouter([]),
        { provide: SalesApi, useValue: api },
        { provide: AuthSessionStore, useValue: session },
        { provide: MatDialog, useValue: dialog },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'order-1' } } },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesOrderDetail);
    fixture.detectChanges();
  });

  it('loads the backend snapshots for the order', () => {
    const component = fixture.componentInstance as unknown as { order: () => unknown };
    expect(api.get).toHaveBeenCalledWith('order-1');
    expect(component.order()).toEqual(order);
  });

  it('cancels a confirmed order after confirmation', () => {
    const component = fixture.componentInstance as unknown as { cancel(): void; order: () => any };

    component.cancel();

    expect(api.cancel).toHaveBeenCalledWith('order-1');
    expect(component.order().status).toBe('CANCELLED');
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
    items: [
      {
        salesOrderItemId: 'item-1',
        salesOrderId: 'order-1',
        productPresentationId: 'presentation-1',
        lotId: null,
        productNameSnapshot: 'Arroz 1 kg',
        skuSnapshot: 'ARROZ-1KG',
        quantity: 1,
        unitPrice: 25.5,
        unitCost: 15,
        discountAmount: 0,
        taxRate: 0.16,
        taxAmount: 4.08,
        lineTotal: 29.58,
      },
    ],
  };
});
