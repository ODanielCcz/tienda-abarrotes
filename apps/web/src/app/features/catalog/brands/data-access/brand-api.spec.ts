import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { Brand } from '../models/brand.model';
import { BrandApi } from './brand-api';

describe('BrandApi', () => {
  let service: BrandApi;
  let httpTesting: HttpTestingController;

  const brand: Brand = {
    id: 'brand-1',
    code: 'COCA_COLA',
    name: 'Coca-Cola',
    status: 'ACTIVE',
    createdAt: '2026-08-20T00:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });

    service = TestBed.inject(BrandApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('creates a brand', () => {
    const payload = { code: 'COCA_COLA', name: 'Coca-Cola' };
    service.create(payload).subscribe((result) => expect(result).toEqual(brand));

    const request = httpTesting.expectOne('/api/v1/catalog/brands');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(response('BRAND_CREATED', brand));
  });

  it('updates a brand', () => {
    const payload = { code: 'COCA_COLA', name: 'Coca-Cola México' };
    service.update(brand.id, payload).subscribe((result) => expect(result.id).toBe(brand.id));

    const request = httpTesting.expectOne(`/api/v1/catalog/brands/${brand.id}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush(response('BRAND_UPDATED', { ...brand, name: payload.name }));
  });

  it('changes brand status', () => {
    service.changeStatus(brand.id, 'INACTIVE').subscribe((result) => {
      expect(result.status).toBe('INACTIVE');
    });

    const request = httpTesting.expectOne(`/api/v1/catalog/brands/${brand.id}/status`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INACTIVE' });
    request.flush(response('BRAND_STATUS_UPDATED', { ...brand, status: 'INACTIVE' }));
  });

  function response(code: string, data: Brand) {
    return {
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code,
      reason: 'OK',
      message: 'Operación correcta',
      data,
      errors: null,
      path: '/api/v1/catalog/brands',
      correlationId: 'brand-api-test',
    };
  }
});
