import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { InventoryApi } from './inventory-api';

describe('InventoryApi', () => {
  let api: InventoryApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(InventoryApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('queries stock, lots, pallets and movements for the selected warehouse', () => {
    const warehouseId = 'warehouse-a';

    api.listStock(warehouseId).subscribe((data) => expect(data).toEqual([]));
    expectGet('/api/v1/inventory/stock', warehouseId);

    api.listLots({ warehouseId, status: 'ACTIVE' }).subscribe((data) => expect(data).toEqual([]));
    const lots = http.expectOne(
      (request) =>
        request.url === '/api/v1/inventory/lots' &&
        request.params.get('warehouseId') === warehouseId &&
        request.params.get('status') === 'ACTIVE',
    );
    lots.flush(response([]));

    api.listPallets(warehouseId).subscribe((data) => expect(data).toEqual([]));
    expectGet('/api/v1/inventory/pallets', warehouseId);

    api.listMovements({ warehouseId, status: 'CONFIRMED' }).subscribe((data) =>
      expect(data).toEqual([]),
    );
    const movements = http.expectOne(
      (request) =>
        request.url === '/api/v1/inventory/movements' &&
        request.params.get('warehouseId') === warehouseId &&
        request.params.get('status') === 'CONFIRMED',
    );
    movements.flush(response([]));
  });

  it('creates an inventory receipt with its idempotency key unchanged', () => {
    const payload = {
      warehouseId: 'warehouse-a',
      supplierId: null,
      idempotencyKey: 'receipt-key-1',
      reason: 'Recepción de prueba',
      items: [
        {
          productPresentationId: 'presentation-1',
          lotNumber: 'LOT-001',
          manufacturedAt: '2026-08-01',
          expiresAt: '2027-08-01',
          quantity: 10,
          unitCost: 18.5,
        },
      ],
      pallets: [],
    };
    const receipt = {
      receiptId: 'receipt-1',
      warehouseId: 'warehouse-a',
      supplierId: null,
      status: 'CONFIRMED',
      receivedAt: '2026-08-29T10:00:00Z',
      items: [],
      pallets: [],
    };

    api.createReceipt(payload).subscribe((data) => expect(data).toEqual(receipt));

    const request = http.expectOne('/api/v1/inventory/receipts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(response(receipt));
  });

  function expectGet(url: string, warehouseId: string): void {
    const request = http.expectOne(
      (candidate) =>
        candidate.url === url && candidate.params.get('warehouseId') === warehouseId,
    );
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  }

  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'FOUND',
      reason: 'OK',
      message: 'OK',
      data,
      errors: null,
      path: '',
      correlationId: 'inventory-test',
    };
  }
});
