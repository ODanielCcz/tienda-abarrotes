import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { SalesReturnApi } from './sales-return-api';

describe('SalesReturnApi', () => {
  let api: SalesReturnApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(SalesReturnApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and creates returns for an order', () => {
    api.listByOrder('order-1').subscribe();
    const list = http.expectOne('/api/v1/sales/orders/order-1/returns');
    expect(list.request.method).toBe('GET');
    list.flush(response([]));

    const payload = {
      reason: 'Producto dañado',
      items: [{ salesOrderItemId: 'item-1', quantity: 1 }],
    };
    api.create('order-1', payload).subscribe();
    const create = http.expectOne('/api/v1/sales/orders/order-1/returns');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush(response(salesReturn));
  });

  it('gets, confirms and cancels a return', () => {
    api.get('return-1').subscribe();
    const get = http.expectOne('/api/v1/sales/returns/return-1');
    expect(get.request.method).toBe('GET');
    get.flush(response(salesReturn));

    api.confirm('return-1', { cashSessionId: 'session-1' }).subscribe();
    const confirm = http.expectOne('/api/v1/sales/returns/return-1/confirm');
    expect(confirm.request.method).toBe('POST');
    expect(confirm.request.body).toEqual({ cashSessionId: 'session-1' });
    confirm.flush(response({ ...salesReturn, status: 'CONFIRMED' }));

    api.cancel('return-1').subscribe();
    const cancel = http.expectOne('/api/v1/sales/returns/return-1/cancel');
    expect(cancel.request.method).toBe('POST');
    cancel.flush(response({ ...salesReturn, status: 'CANCELLED' }));
  });

  const salesReturn = {
    returnId: 'return-1',
    salesOrderId: 'order-1',
    status: 'DRAFT',
    reason: 'Producto dañado',
    total: 29.58,
    createdBy: 'user-1',
    createdAt: '2026-08-29T00:00:00Z',
    confirmedAt: null,
    items: [],
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
      correlationId: 'return-test',
    };
  }
});
