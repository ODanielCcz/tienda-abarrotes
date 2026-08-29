import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { BrandApi } from '../../../brands/data-access/brand-api';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { ProductApi } from '../../data-access/product-api';
import { ProductList, readProductListQueryParams } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  const productApi = { list: vi.fn() };
  const brandApi = { list: vi.fn() };
  const categoryApi = { list: vi.fn() };
  const sessionStore = { hasPermission: vi.fn() };

  it('restores filters and pagination from query parameters', () => {
    expect(
      readProductListQueryParams(
        convertToParamMap({
          page: '2',
          size: '50',
          search: 'arroz',
          status: 'ACTIVE',
          brandId: 'brand-1',
          categoryId: 'category-1',
        }),
      ),
    ).toEqual({
      page: 2,
      size: 50,
      search: 'arroz',
      status: 'ACTIVE',
      brandId: 'brand-1',
      categoryId: 'category-1',
    });
  });

  beforeEach(async () => {
    sessionStore.hasPermission.mockReturnValue(true);
    productApi.list.mockReturnValue(
      of({
        content: [
          {
            id: 'product-1',
            categoryId: 'category-1',
            brandId: 'brand-1',
            name: 'Arroz 1 kg',
            description: null,
            productType: 'GOODS',
            tracksInventory: true,
            tracksLots: true,
            tracksExpiration: true,
            status: 'ACTIVE',
            createdAt: '2026-08-20T00:00:00Z',
            updatedAt: '2026-08-20T00:00:00Z',
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }),
    );
    brandApi.list.mockReturnValue(
      of([{ id: 'brand-1', code: 'MARCA', name: 'Marca', status: 'ACTIVE', createdAt: '' }]),
    );
    categoryApi.list.mockReturnValue(
      of([
        {
          id: 'category-1',
          parentCategoryId: null,
          code: 'ABARROTES',
          name: 'Abarrotes',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [
        provideRouter([]),
        { provide: ProductApi, useValue: productApi },
        { provide: BrandApi, useValue: brandApi },
        { provide: CategoryApi, useValue: categoryApi },
        { provide: AuthSessionStore, useValue: sessionStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductList);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders products returned by the API', () => {
    expect(productApi.list).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, sortBy: 'NAME', direction: 'ASC' }),
    );
    expect(fixture.nativeElement.textContent).toContain('Arroz 1 kg');
    expect(fixture.nativeElement.textContent).toContain('Marca');
    expect(fixture.nativeElement.textContent).toContain('Abarrotes');
  });

  it('links every product to its detail page', () => {
    const link = fixture.nativeElement.querySelector(
      'a[aria-label="Ver detalle de Arroz 1 kg"]',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toContain('/catalog/products/product-1?');
    expect(link?.getAttribute('href')).toContain('page=0');
    expect(link?.getAttribute('href')).toContain('size=20');
  });

  it('shows the create action only with product create permission', () => {
    const createLink = fixture.nativeElement.querySelector(
      '[data-testid="create-product"]',
    ) as HTMLAnchorElement | null;

    expect(createLink?.getAttribute('href')).toContain('/catalog/products/new');
  });

  it('shows the backend error reference', async () => {
    productApi.list.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { message: 'Error de productos', correlationId: 'ref-1' },
          }),
      ),
    );

    const component = fixture.componentInstance as unknown as { refresh(): void };
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Error de productos (Referencia: ref-1)',
    );
  });
});
