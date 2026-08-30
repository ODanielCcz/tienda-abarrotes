import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../../core/context/operational-context.store';
import { CashApi } from '../../../../cash/data-access/cash-api';
import { SalesOrder } from '../../../models/sales.model';
import { SalesPaymentApi } from '../../data-access/sales-payment-api';
import { SalesPayment } from '../../models/sales-payment.model';
import { SalesPaymentPanel } from './sales-payment-panel';

describe('SalesPaymentPanel', () => {
  let fixture: ComponentFixture<SalesPaymentPanel>;
  const api = { list: vi.fn(), create: vi.fn(), cancel: vi.fn() };
  const cashApi = { listSessions: vi.fn() };
  const dialog = { open: vi.fn() };
  const context = { context: vi.fn(() => ({ cashRegisterId: 'register-1' })) };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.list.mockReturnValue(of([]));
    cashApi.listSessions.mockReturnValue(of([cashSession]));
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    await TestBed.configureTestingModule({
      imports: [SalesPaymentPanel],
      providers: [
        { provide: SalesPaymentApi, useValue: api },
        { provide: CashApi, useValue: cashApi },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SalesPaymentPanel);
    fixture.componentRef.setInput('order', order);
    fixture.detectChanges();
  });

  it('loads payments and the open cash session', () => {
    const component = expose(fixture);
    expect(api.list).toHaveBeenCalledWith('order-1');
    expect(cashApi.listSessions).toHaveBeenCalledWith({
      cashRegisterId: 'register-1',
      status: 'OPEN',
    });
    expect(component.openCashSession()).toEqual(cashSession);
  });

  it('creates a cash payment with the open session and stable idempotency key', () => {
    const component = expose(fixture);
    const key = component.idempotencyKey;
    component.paymentForm.patchValue({ paymentMethod: 'CASH', amount: 42.92, reference: '' });
    api.create.mockReturnValueOnce(of(payment));

    component.createPayment();

    expect(api.create).toHaveBeenCalledWith('order-1', {
      cashSessionId: 'session-1',
      paymentMethod: 'CASH',
      amount: 42.92,
      currencyCode: 'MXN',
      reference: null,
      idempotencyKey: key,
    });
  });

  it('keeps the key when payment creation fails', () => {
    const component = expose(fixture);
    const key = component.idempotencyKey;
    component.paymentForm.patchValue({ paymentMethod: 'CASH', amount: 20, reference: '' });
    api.create.mockReturnValueOnce(throwError(() => new Error('rejected')));

    component.createPayment();

    expect(component.idempotencyKey).toBe(key);
  });

  it('cancels a captured payment after confirmation', () => {
    const component = expose(fixture);
    api.cancel.mockReturnValueOnce(of({ ...payment, status: 'CANCELLED' }));

    component.cancelPayment(payment);

    expect(api.cancel).toHaveBeenCalledWith('payment-1');
  });

  function expose(target: ComponentFixture<SalesPaymentPanel>) {
    return target.componentInstance as unknown as {
      paymentForm: FormGroup;
      idempotencyKey: string;
      openCashSession: () => unknown;
      createPayment(): void;
      cancelPayment(payment: SalesPayment): void;
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
    paymentStatus: 'PENDING',
    currencyCode: 'MXN',
    subtotal: 37,
    discountTotal: 0,
    taxTotal: 5.92,
    total: 42.92,
    idempotencyKey: 'order-key',
    createdAt: '',
    confirmedAt: '',
    cancelledAt: null,
    items: [],
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
  const payment: SalesPayment = {
    paymentId: 'payment-1',
    salesOrderId: 'order-1',
    cashSessionId: 'session-1',
    paymentMethod: 'CASH',
    status: 'CAPTURED',
    amount: 42.92,
    currencyCode: 'MXN',
    reference: null,
    idempotencyKey: 'payment-key',
    paidAt: '',
    createdAt: '',
  };
});
