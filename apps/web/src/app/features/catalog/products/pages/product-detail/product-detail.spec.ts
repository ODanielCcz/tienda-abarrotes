import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrandApi } from '../../../brands/data-access/brand-api';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { ProductApi } from '../../data-access/product-api';
import { ProductDetail } from './product-detail';

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;
  const productApi = {
    getById: vi.fn(),
    listPresentations: vi.fn(),
  };
  const brandApi = { list: vi.fn() };
  const categoryApi = { list: vi.fn() };

  beforeEach(async () => {
    productApi.getById.mockReturnValue(
      of({
        id: 'product-1',
        categoryId: 'category-1',
        brandId: 'brand-1',
        name: 'Arroz 1 kg',
        description: 'Arroz de grano largo',
        productType: 'GOODS',
        tracksInventory: true,
        tracksLots: true,
        tracksExpiration: true,
        status: 'ACTIVE',
        createdAt: '2026-08-20T00:00:00Z',
        updatedAt: '2026-08-20T00:00:00Z',
      }),
    );
    productApi.listPresentations.mockReturnValue(
      of([
        {
          id: 'presentation-1',
          productId: 'product-1',
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
      ]),
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
      imports: [ProductDetail],
      providers: [
        { provide: ProductApi, useValue: productApi },
        { provide: BrandApi, useValue: brandApi },
        { provide: CategoryApi, useValue: categoryApi },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ productId: 'product-1' }),
              queryParams: { search: 'arroz', page: '1', size: '20' },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders product information and sellable presentations', () => {
    expect(productApi.getById).toHaveBeenCalledWith('product-1');
    expect(productApi.listPresentations).toHaveBeenCalledWith('product-1');
    expect(fixture.nativeElement.textContent).toContain('Arroz 1 kg');
    expect(fixture.nativeElement.textContent).toContain('Marca');
    expect(fixture.nativeElement.textContent).toContain('Abarrotes');
    expect(fixture.nativeElement.textContent).toContain('Bolsa 1 kg');
    expect(fixture.nativeElement.textContent).toContain('ARROZ-1KG');
    expect(fixture.nativeElement.textContent).toContain('unit-1');
    expect(fixture.nativeElement.textContent).toContain('tax-1');

    const backLink = fixture.nativeElement.querySelector(
      'a[aria-label="Volver al listado de productos"]',
    ) as HTMLAnchorElement | null;
    expect(backLink?.getAttribute('href')).toContain('search=arroz');
    expect(backLink?.getAttribute('href')).toContain('page=1');
  });

  it('shows the backend reference when detail loading fails', async () => {
    productApi.getById.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { message: 'Producto no encontrado', correlationId: 'product-ref-404' },
          }),
      ),
    );

    const component = fixture.componentInstance as unknown as { refresh(): void };
    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Producto no encontrado (Referencia: product-ref-404)',
    );
  });
});
