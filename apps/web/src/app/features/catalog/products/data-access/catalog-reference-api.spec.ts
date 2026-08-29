import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { CatalogReferenceApi } from './catalog-reference-api';

describe('CatalogReferenceApi', () => {
  let api: CatalogReferenceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(CatalogReferenceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists units and active taxes', () => {
    const unit = { id: 'unit-1', code: 'PZA', name: 'Pieza', symbol: 'pza', quantityScale: 0 };
    const tax = { id: 'tax-1', code: 'IVA16', name: 'IVA 16%', rate: 0.16 };

    api.listUnits().subscribe((result) => expect(result).toEqual([unit]));
    http.expectOne('/api/v1/catalog/units').flush(apiResponse([unit]));

    api.listTaxes().subscribe((result) => expect(result).toEqual([tax]));
    http.expectOne('/api/v1/catalog/taxes').flush(apiResponse([tax]));
  });

  function apiResponse<T>(data: T) {
    return {
      timestamp: '2026-08-29T00:00:00Z',
      status: 200,
      code: 'FOUND',
      reason: 'OK',
      message: 'Datos consultados',
      data,
      errors: null,
      path: '/api/v1/catalog',
      correlationId: 'reference-test',
    };
  }
});
