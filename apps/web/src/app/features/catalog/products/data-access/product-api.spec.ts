import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { Product, ProductPresentation } from '../models/product.model';
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

  it('gets one product by id', () => {
    const product = page.content[0];

    service.getById(product.id).subscribe((result) => expect(result).toEqual(product));

    const request = httpTesting.expectOne(`/api/v1/catalog/products/${product.id}`);
    expect(request.request.method).toBe('GET');
    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'PRODUCT_FOUND',
      reason: 'OK',
      message: 'Producto consultado',
      data: product,
      errors: null,
      path: `/api/v1/catalog/products/${product.id}`,
      correlationId: 'product-detail-test',
    });
  });

  it('lists product presentations', () => {
    const productId = page.content[0].id;
    const presentations: ProductPresentation[] = [
      {
        id: 'presentation-1',
        productId,
        unitId: 'unit-1',
        taxId: 'tax-1',
        sku: 'ARROZ-1KG',
        name: 'Bolsa 1 kg',
        conversionFactor: 1,
        netContent: 1000,
        minimumStock: 10,
        status: 'ACTIVE',
        createdAt: '2026-08-20T00:00:00Z',
        updatedAt: '2026-08-20T00:00:00Z',
      },
    ];

    service
      .listPresentations(productId)
      .subscribe((result) => expect(result).toEqual(presentations));

    const request = httpTesting.expectOne(
      `/api/v1/catalog/products/${productId}/presentations`,
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'PRODUCT_PRESENTATIONS_FOUND',
      reason: 'OK',
      message: 'Presentaciones consultadas',
      data: presentations,
      errors: null,
      path: `/api/v1/catalog/products/${productId}/presentations`,
      correlationId: 'product-presentations-test',
    });
  });

  it('creates a product', () => {
    const payload = {
      categoryId: page.content[0].categoryId,
      brandId: page.content[0].brandId,
      name: 'Arroz 1 kg',
      description: null,
      productType: 'GOODS' as const,
      tracksInventory: true,
      tracksLots: true,
      tracksExpiration: true,
    };
    service.create(payload).subscribe((result) => expect(result.id).toBe(page.content[0].id));

    const request = httpTesting.expectOne('/api/v1/catalog/products');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(productResponse('PRODUCT_CREATED', page.content[0]));
  });

  it('updates a product', () => {
    const product = page.content[0];
    const payload = {
      categoryId: product.categoryId,
      brandId: product.brandId,
      name: 'Arroz premium 1 kg',
      description: product.description,
      productType: product.productType,
      tracksInventory: product.tracksInventory,
      tracksLots: product.tracksLots,
      tracksExpiration: product.tracksExpiration,
    };
    service.update(product.id, payload).subscribe((result) => expect(result.name).toBe(payload.name));

    const request = httpTesting.expectOne(`/api/v1/catalog/products/${product.id}`);
    expect(request.request.method).toBe('PUT');
    request.flush(productResponse('PRODUCT_UPDATED', { ...product, name: payload.name }));
  });

  it('changes product status', () => {
    const product = page.content[0];
    service.changeStatus(product.id, 'INACTIVE').subscribe((result) => {
      expect(result.status).toBe('INACTIVE');
    });

    const request = httpTesting.expectOne(`/api/v1/catalog/products/${product.id}/status`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INACTIVE' });
    request.flush(productResponse('PRODUCT_STATUS_UPDATED', { ...product, status: 'INACTIVE' }));
  });

  function productResponse(code: string, data: Product) {
    return {
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code,
      reason: 'OK',
      message: 'Operación correcta',
      data,
      errors: null,
      path: '/api/v1/catalog/products',
      correlationId: 'product-write-test',
    };
  }
});
