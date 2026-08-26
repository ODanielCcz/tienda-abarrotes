import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { Product } from '../models/product.model';
import { ProductApi } from './product-api';

describe('ProductApi', () => {
  let service: ProductApi;
  let httpTesting: HttpTestingController;

  const page: PageResponse<Product> = {
    content: [
      {
        id: '00000000-0000-0000-0000-000000000401',
        categoryId: '00000000-0000-0000-0000-000000000201',
        brandId: '00000000-0000-0000-0000-000000000301',
        name: 'Arroz 1 kg',
        description: 'Arroz de grano largo',
        productType: 'GOODS',
        tracksInventory: true,
        tracksLots: true,
        tracksExpiration: true,
        status: 'ACTIVE',
        createdAt: '2026-08-20T00:00:00Z',
        updatedAt: '2026-08-20T00:00:00Z',
      },
    ],
    page: 1,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    first: false,
    last: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });

    service = TestBed.inject(ProductApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists products using backend pagination and filters', () => {
    service
      .list({
        page: 1,
        size: 20,
        search: 'arroz',
        status: 'ACTIVE',
        categoryId: '00000000-0000-0000-0000-000000000201',
        brandId: '00000000-0000-0000-0000-000000000301',
        sortBy: 'NAME',
        direction: 'ASC',
      })
      .subscribe((result) => expect(result).toEqual(page));

    const request = httpTesting.expectOne((candidate) => {
      const params = candidate.params;
      return (
        candidate.url === '/api/v1/catalog/products' &&
        params.get('page') === '1' &&
        params.get('size') === '20' &&
        params.get('search') === 'arroz' &&
        params.get('status') === 'ACTIVE' &&
        params.get('categoryId') === '00000000-0000-0000-0000-000000000201' &&
        params.get('brandId') === '00000000-0000-0000-0000-000000000301' &&
        params.get('sortBy') === 'NAME' &&
        params.get('direction') === 'ASC'
      );
    });

    expect(request.request.method).toBe('GET');
    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'PRODUCTS_FOUND',
      reason: 'OK',
      message: 'Productos consultados',
      data: page,
      errors: null,
      path: '/api/v1/catalog/products',
      correlationId: 'products-page-test',
    });
  });
});
