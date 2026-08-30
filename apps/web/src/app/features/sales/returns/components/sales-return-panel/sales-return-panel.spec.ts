import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../../core/context/operational-context.store';
import { CashApi } from '../../../../cash/data-access/cash-api';
import { SalesOrder } from '../../../models/sales.model';
import { SalesPaymentApi } from '../../../payments/data-access/sales-payment-api';
import { SalesReturnApi } from '../../data-access/sales-return-api';
import { SalesReturn } from '../../models/sales-return.model';
import { SalesReturnPanel } from './sales-return-panel';

describe('SalesReturnPanel', () => {
  let fixture: ComponentFixture<SalesReturnPanel>;
  const api = { listByOrder: vi.fn(), create: vi.fn(), confirm: vi.fn(), cancel: vi.fn() };
  const paymentApi = { list: vi.fn() };
  const cashApi = { listSessions: vi.fn() };
  const context = { context: vi.fn(() => ({ cashRegisterId: 'register-1' })) };
  const session = { hasPermission: vi.fn(() => true) };
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.listByOrder.mockReturnValue(of([]));
    paymentApi.list.mockReturnValue(of([cashPayment]));
    cashApi.listSessions.mockReturnValue(of([cashSession]));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    await TestBed.configureTestingModule({
      imports: [SalesReturnPanel],
      providers: [
        { provide: SalesReturnApi, useValue: api },
        { provide: SalesPaymentApi, useValue: paymentApi },
        { provide: CashApi, useValue: cashApi },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesReturnPanel);
    fixture.componentRef.setInput('order', order);
    fixture.detectChanges();
  });

  it('loads existing returns, payments and the open cash session', () => {
    const component = expose(fixture);
    expect(api.listByOrder).toHaveBeenCalledWith('order-1');
    expect(paymentApi.list).toHaveBeenCalledWith('order-1');
    expect(component.openCashSession()).toEqual(cashSession);
  });

  it('creates a draft using selected sold items', () => {
    const component = expose(fixture);
    component.returnForm.controls['reason'].setValue('Producto dañado');
    component.items.at(0).controls['quantity'].setValue(1);
    api.create.mockReturnValueOnce(of(draft));

    component.createReturn();

    expect(api.create).toHaveBeenCalledWith('order-1', {
      reason: 'Producto dañado',
      items: [{ salesOrderItemId: 'item-1', quantity: 1 }],
    });
  });

  it('confirms a cash return with the open session', () => {
    const component = expose(fixture);
    api.confirm.mockReturnValueOnce(of({ ...draft, status: 'CONFIRMED' }));

    component.confirmReturn(draft);

    expect(api.confirm).toHaveBeenCalledWith('return-1', { cashSessionId: 'session-1' });
  });

  it('cancels a draft after confirmation', () => {
    const component = expose(fixture);
    api.cancel.mockReturnValueOnce(of({ ...draft, status: 'CANCELLED' }));

    component.cancelReturn(draft);

    expect(api.cancel).toHaveBeenCalledWith('return-1');
  });

  function expose(target: ComponentFixture<SalesReturnPanel>) {
    return target.componentInstance as unknown as {
      returnForm: FormGroup;
      items: FormArray<FormGroup>;
      openCashSession: () => unknown;
      createReturn(): void;
      confirmReturn(salesReturn: SalesReturn): void;
      cancelReturn(salesReturn: SalesReturn): void;
    };
  }

  const order: SalesOrder = {
    salesOrderId: 'order-1',
    orderNumber: 'SO-0001',
    branchId: 'branch-1',
    warehouseId: 'warehouse-1',
    customerId: null,
    deviceId: null,
    channel: 'POS',
    status: 'CONFIRMED',
    paymentStatus: 'PAID',
    currencyCode: 'MXN',
    subtotal: 25.5,
    discountTotal: 0,
    taxTotal: 4.08,
    total: 29.58,
    idempotencyKey: 'order-key',
    createdAt: '',
    confirmedAt: '',
    cancelledAt: null,
    items: [
      {
        salesOrderItemId: 'item-1',
        salesOrderId: 'order-1',
        productPresentationId: 'presentation-1',
        lotId: null,
        productNameSnapshot: 'Arroz 1 kg',
        skuSnapshot: 'ARROZ-1KG',
        quantity: 2,
        unitPrice: 25.5,
        unitCost: 15,
        discountAmount: 0,
        taxRate: 0.16,
        taxAmount: 8.16,
        lineTotal: 59.16,
      },
    ],
  };
  const draft: SalesReturn = {
    returnId: 'return-1',
    salesOrderId: 'order-1',
    status: 'DRAFT',
    reason: 'Producto dañado',
    total: 29.58,
    createdBy: 'user-1',
    createdAt: '',
    confirmedAt: null,
    items: [],
  };
  const cashPayment = {
    paymentId: 'payment-1',
    salesOrderId: 'order-1',
    cashSessionId: 'session-1',
    paymentMethod: 'CASH',
    status: 'CAPTURED',
    amount: 29.58,
    currencyCode: 'MXN',
    reference: null,
    idempotencyKey: 'payment-key',
    paidAt: '',
    createdAt: '',
  };
  const cashSession = {
    cashSessionId: 'session-1',
    cashRegisterId: 'register-1',
    branchId: 'branch-1',
    cashRegisterCode: 'CAJA-1',
    cashRegisterName: 'Caja principal',
    openedBy: 'user-1',
    closedBy: null,
    status: 'OPEN',
    openingAmount: 1000,
    expectedAmount: 1000,
    countedAmount: null,
    differenceAmount: null,
    openedAt: '',
    closedAt: null,
    notes: null,
  };
});
