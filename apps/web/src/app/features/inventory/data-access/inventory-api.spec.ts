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

    api
      .listMovements({ warehouseId, status: 'CONFIRMED' })
      .subscribe((data) => expect(data).toEqual([]));
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

  it('sends advanced inventory operations to their dedicated endpoints', () => {
    const adjustment = {
      warehouseId: 'warehouse-a',
      reason: 'Conteo extraordinario',
      items: [
        {
          productPresentationId: 'presentation-1',
          lotId: null,
          direction: 'IN' as const,
          quantity: 2,
          unitCost: 10,
        },
      ],
    };
    api.adjust(adjustment).subscribe();
    expectPost('/api/v1/inventory/adjustments', adjustment, {});

    const transfer = {
      fromWarehouseId: 'warehouse-a',
      toWarehouseId: 'warehouse-b',
      reason: null,
      items: [
        { productPresentationId: 'presentation-1', lotId: null, quantity: 1, unitCost: null },
      ],
    };
    api.transfer(transfer).subscribe();
    expectPost('/api/v1/inventory/transfers', transfer, []);

    const count = {
      warehouseId: 'warehouse-a',
      items: [{ productPresentationId: 'presentation-1', lotId: null, countedQuantity: 9 }],
    };
    api.createCount(count).subscribe();
    expectPost('/api/v1/inventory/counts', count, { inventoryCountId: 'count-1' });

    api.confirmCount('count-1').subscribe();
    expectPost('/api/v1/inventory/counts/count-1/confirm', {}, { inventoryCountId: 'count-1' });
  });

  function expectGet(url: string, warehouseId: string): void {
    const request = http.expectOne(
      (candidate) => candidate.url === url && candidate.params.get('warehouseId') === warehouseId,
    );
    expect(request.request.method).toBe('GET');
    request.flush(response([]));
  }

  function expectPost<T>(url: string, body: unknown, data: T): void {
    const request = http.expectOne(url);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(response(data));
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
