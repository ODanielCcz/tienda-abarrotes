import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../../core/config/api.config';
import { Category, CategoryTreeNode } from '../models/category.model';
import { CategoryApi } from './category-api.service';

describe('CategoryApi', () => {
  let service: CategoryApi;
  let httpTesting: HttpTestingController;

  const categoryTree: CategoryTreeNode[] = [
    {
      id: '00000000-0000-0000-0000-000000000201',
      parentCategoryId: null,
      code: 'BEBIDAS',
      name: 'Bebidas',
      status: 'ACTIVE',
      createdAt: '2026-08-19T00:00:00Z',
      updatedAt: '2026-08-19T00:00:00Z',
      children: [],
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: API_CONFIG,
          useValue: { baseUrl: '/api/v1' },
        },
      ],
    });

    service = TestBed.inject(CategoryApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('requests the active category tree', () => {
    service.tree('ACTIVE').subscribe((result) => expect(result).toEqual(categoryTree));

    const request = httpTesting.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/catalog/categories/tree' &&
        candidate.params.get('status') === 'ACTIVE',
    );

    expect(request.request.method).toBe('GET');

    request.flush({
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code: 'CATEGORY_TREE_FOUND',
      reason: 'OK',
      message: 'Árbol consultado',
      data: categoryTree,
      errors: null,
      path: '/api/v1/catalog/categories/tree',
      correlationId: 'category-tree-test',
    });
  });

  it('creates a category', () => {
    const payload = { code: 'BEBIDAS', name: 'Bebidas', parentCategoryId: null };
    const category = categoryTree[0];
    service.create(payload).subscribe((result) => expect(result).toEqual(category));

    const request = httpTesting.expectOne('/api/v1/catalog/categories');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(response('CATEGORY_CREATED', category));
  });

  it('updates a category', () => {
    const category = categoryTree[0];
    const payload = { code: 'BEBIDAS', name: 'Bebidas y refrescos', parentCategoryId: null };
    service.update(category.id, payload).subscribe((result) => expect(result.name).toBe(payload.name));

    const request = httpTesting.expectOne(`/api/v1/catalog/categories/${category.id}`);
    expect(request.request.method).toBe('PUT');
    request.flush(response('CATEGORY_UPDATED', { ...category, name: payload.name }));
  });

  it('changes category status', () => {
    const category = categoryTree[0];
    service.changeStatus(category.id, 'INACTIVE').subscribe((result) => {
      expect(result.status).toBe('INACTIVE');
    });

    const request = httpTesting.expectOne(`/api/v1/catalog/categories/${category.id}/status`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ status: 'INACTIVE' });
    request.flush(response('CATEGORY_STATUS_UPDATED', { ...category, status: 'INACTIVE' }));
  });

  function response(code: string, data: Category) {
    return {
      timestamp: '2026-08-25T00:00:00Z',
      status: 200,
      code,
      reason: 'OK',
      message: 'Operación correcta',
      data,
      errors: null,
      path: '/api/v1/catalog/categories',
      correlationId: 'category-api-test',
    };
  }
});
