import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { PurchasingApi } from './purchasing-api';

describe('PurchasingApi', () => {
  let api: PurchasingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(PurchasingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and writes suppliers', () => {
    api.listSuppliers({ search: 'arroz', status: 'ACTIVE' }).subscribe();
    const list = http.expectOne(
      (request) =>
        request.url === '/api/v1/purchasing/suppliers' &&
        request.params.get('search') === 'arroz' &&
        request.params.get('status') === 'ACTIVE',
    );
    expect(list.request.method).toBe('GET');
    list.flush(response([]));

    const payload = {
      supplierCode: 'PROV-01',
      legalName: 'Proveedor Uno SA de CV',
      tradeName: 'Proveedor Uno',
      taxId: 'PUO010101AA1',
      email: 'ventas@example.com',
      phone: '5555555555',
      creditDays: 15,
    };
    api.createSupplier(payload).subscribe();
    const create = http.expectOne('/api/v1/purchasing/suppliers');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush(response(supplier));
  });

  it('creates, confirms and receives a purchase', () => {
    const payload = {
      warehouseId: 'warehouse-1',
      supplierId: 'supplier-1',
      supplierDocument: 'FAC-001',
      currencyCode: 'MXN',
      idempotencyKey: 'purchase-key-1',
      items: [
        {
          productPresentationId: 'presentation-1',
          quantity: 10,
          unitCost: 20,
          discountAmount: 0,
          taxAmount: 32,
        },
      ],
    };
    api.createPurchase(payload).subscribe();
    const create = http.expectOne('/api/v1/purchasing/purchases');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(payload);
    create.flush(response(purchase));

    api.confirmPurchase('purchase-1').subscribe();
    const confirm = http.expectOne('/api/v1/purchasing/purchases/purchase-1/confirm');
    expect(confirm.request.method).toBe('POST');
    confirm.flush(response({ ...purchase, status: 'CONFIRMED' }));

    const receivePayload = {
      idempotencyKey: 'receipt-key-1',
      items: [
        {
          purchaseItemId: 'item-1',
          lotNumber: 'LOT-001',
          manufacturedAt: null,
          expiresAt: '2027-08-01',
          quantity: 10,
        },
      ],
      pallets: [],
    };
    api.receivePurchase('purchase-1', receivePayload).subscribe();
    const receive = http.expectOne('/api/v1/purchasing/purchases/purchase-1/receive');
    expect(receive.request.method).toBe('POST');
    expect(receive.request.body).toEqual(receivePayload);
    receive.flush(response({ receiptId: 'receipt-1' }));
  });

  const supplier = {
    supplierId: 'supplier-1',
    supplierCode: 'PROV-01',
    legalName: 'Proveedor Uno SA de CV',
    tradeName: 'Proveedor Uno',
    taxId: 'PUO010101AA1',
    email: 'ventas@example.com',
    phone: '5555555555',
    creditDays: 15,
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };

  const purchase = {
    purchaseId: 'purchase-1',
    branchId: 'branch-1',
    warehouseId: 'warehouse-1',
    supplierId: 'supplier-1',
    supplierDocument: 'FAC-001',
    status: 'DRAFT',
    paymentStatus: 'PENDING',
    currencyCode: 'MXN',
    subtotal: 200,
    discountTotal: 0,
    taxTotal: 32,
    total: 232,
    idempotencyKey: 'purchase-key-1',
    purchasedAt: '',
    confirmedAt: null,
    createdAt: '',
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
      correlationId: 'purchasing-test',
    };
  }
});
