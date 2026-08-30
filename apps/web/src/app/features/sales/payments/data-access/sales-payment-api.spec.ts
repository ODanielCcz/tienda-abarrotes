import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { SalesPaymentApi } from './sales-payment-api';

describe('SalesPaymentApi', () => {
  let api: SalesPaymentApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(SalesPaymentApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and creates payments for an order', () => {
    api.list('order-1').subscribe();
    const list = http.expectOne('/api/v1/sales/orders/order-1/payments');
    expect(list.request.method).toBe('GET');
    list.flush(response([]));

    const payload = {
      cashSessionId: 'session-1',
      paymentMethod: 'CASH' as const,
      amount: 42.92,
      currencyCode: 'MXN',
      reference: null,
      idempotencyKey: 'payment-key-1',
    };
    api.create('order-1', payload).subscribe();
    const create = http.expectOne('/api/v1/sales/orders/order-1/payments');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush(response(payment));
  });

  it('gets and cancels a payment', () => {
    api.get('payment-1').subscribe();
    const get = http.expectOne('/api/v1/sales/payments/payment-1');
    expect(get.request.method).toBe('GET');
    get.flush(response(payment));

    api.cancel('payment-1').subscribe();
    const cancel = http.expectOne('/api/v1/sales/payments/payment-1/cancel');
    expect(cancel.request.method).toBe('POST');
    cancel.flush(response({ ...payment, status: 'CANCELLED' }));
  });

  const payment = {
    paymentId: 'payment-1',
    salesOrderId: 'order-1',
    cashSessionId: 'session-1',
    paymentMethod: 'CASH',
    status: 'CAPTURED',
    amount: 42.92,
    currencyCode: 'MXN',
    reference: null,
    idempotencyKey: 'payment-key-1',
    paidAt: '2026-08-29T00:00:00Z',
    createdAt: '2026-08-29T00:00:00Z',
  };

  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'OK',
      reason: 'OK',
      message: 'OK',
      data,
      errors: null,
      path: '',
      correlationId: 'payment-test',
    };
  }
});
