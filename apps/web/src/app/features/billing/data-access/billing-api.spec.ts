import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { BillingApi } from './billing-api';

describe('BillingApi', () => {
  let api: BillingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(BillingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and writes issuer and receiver profiles', () => {
    api.listIssuerProfiles('branch-1', 'ACTIVE').subscribe();
    http
      .expectOne('/api/v1/billing/issuer-profiles?branchId=branch-1&status=ACTIVE')
      .flush(response([]));

    api.listFiscalProfiles('customer-1', 'ACTIVE').subscribe();
    http
      .expectOne('/api/v1/billing/fiscal-profiles?customerId=customer-1&status=ACTIVE')
      .flush(response([]));

    api.createIssuerProfile(issuerRequest()).subscribe();
    expect(http.expectOne('/api/v1/billing/issuer-profiles').request.method).toBe('POST');

    api.createFiscalProfile(fiscalRequest()).subscribe();
    expect(http.expectOne('/api/v1/billing/fiscal-profiles').request.method).toBe('POST');
  });

  it('creates, reads and marks an internal document ready', () => {
    api.listFiscalDocuments('sale-1', 'DRAFT').subscribe();
    http
      .expectOne('/api/v1/billing/fiscal-documents?salesOrderId=sale-1&status=DRAFT')
      .flush(response([]));

    api
      .createFiscalDocument({
        salesOrderId: 'sale-1',
        issuerProfileId: 'issuer-1',
        fiscalProfileId: 'profile-1',
        series: 'A',
        folio: '1',
        paymentFormCode: '01',
        paymentMethodCode: 'PUE',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/billing/fiscal-documents').request.method).toBe('POST');

    api.markReady('document-1').subscribe();
    expect(http.expectOne('/api/v1/billing/fiscal-documents/document-1/ready').request.method).toBe(
      'POST',
    );
  });

  it('updates SAT classification for products and units', () => {
    api.classifyProduct('product-1', '50161509').subscribe();
    const product = http.expectOne('/api/v1/catalog/products/product-1/fiscal-classification');
    expect(product.request.body).toEqual({ satProductServiceCode: '50161509' });

    api.classifyUnit('unit-1', 'H87').subscribe();
    const unit = http.expectOne('/api/v1/catalog/units/unit-1/fiscal-classification');
    expect(unit.request.body).toEqual({ satUnitCode: 'H87' });
  });

  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'OK',
      reason: 'OK',
      message: 'ok',
      data,
      errors: null,
      path: '',
      correlationId: 'billing-test',
    };
  }
  function issuerRequest() {
    return {
      branchId: 'branch-1',
      rfc: 'AAA010101AAA',
      legalName: 'Emisor',
      postalCode: '06000',
      fiscalRegimeCode: '601',
      defaultSeries: 'A',
    };
  }
  function fiscalRequest() {
    return {
      customerId: 'customer-1',
      rfc: 'XAXX010101000',
      legalName: 'Cliente',
      postalCode: '06000',
      fiscalRegimeCode: '616',
      cfdiUseCode: 'S01',
      email: null,
    };
  }
});
