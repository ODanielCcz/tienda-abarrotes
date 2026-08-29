import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrandApi } from '../../../brands/data-access/brand-api';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { ProductApi } from '../../data-access/product-api';
import { ProductList } from './product-list';

describe('ProductList', () => {
  let fixture: ComponentFixture<ProductList>;
  const productApi = { list: vi.fn() };
  const brandApi = { list: vi.fn() };
  const categoryApi = { list: vi.fn() };

  beforeEach(async () => {
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

    expect(link?.getAttribute('href')).toBe('/catalog/products/product-1');
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
