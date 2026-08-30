import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { SalesApi } from './sales-api';

describe('SalesApi', () => {
  let api: SalesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(SalesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists sales using operational filters', () => {
    api
      .list({ warehouseId: 'warehouse-1', customerId: 'customer-1', status: 'CONFIRMED' })
      .subscribe();

    const request = http.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/sales/orders' &&
        candidate.params.get('warehouseId') === 'warehouse-1' &&
        candidate.params.get('customerId') === 'customer-1' &&
        candidate.params.get('status') === 'CONFIRMED',
    );
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  });

  it('creates, gets and cancels a sales order', () => {
    const payload = {
      warehouseId: 'warehouse-1',
      customerId: null,
      deviceId: null,
      channel: 'POS' as const,
      currencyCode: 'MXN',
      idempotencyKey: 'order-key-1',
      items: [
        {
          productPresentationId: 'presentation-1',
          quantity: 2,
          unitPrice: 18.5,
          discountAmount: 0,
        },
      ],
    };

    api.create(payload).subscribe();
    const create = http.expectOne('/api/v1/sales/orders');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush(response(order));

    api.get('order-1').subscribe();
    const get = http.expectOne('/api/v1/sales/orders/order-1');
    expect(get.request.method).toBe('GET');
    get.flush(response(order));

    api.cancel('order-1').subscribe();
    const cancel = http.expectOne('/api/v1/sales/orders/order-1/cancel');
    expect(cancel.request.method).toBe('POST');
    cancel.flush(response({ ...order, status: 'CANCELLED' }));
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
    subtotal: 37,
    discountTotal: 0,
    taxTotal: 5.92,
    total: 42.92,
    idempotencyKey: 'order-key-1',
    createdAt: '2026-08-29T00:00:00Z',
    confirmedAt: '2026-08-29T00:00:00Z',
    cancelledAt: null,
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
      correlationId: 'sales-test',
    };
  }
});
