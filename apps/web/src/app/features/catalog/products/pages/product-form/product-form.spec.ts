import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BrandApi } from '../../../brands/data-access/brand-api';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { ProductApi } from '../../data-access/product-api';
import { Product } from '../../models/product.model';
import { ProductForm, productTrackingError } from './product-form';

describe('ProductForm', () => {
  let fixture: ComponentFixture<ProductForm>;
  const product: Product = {
    id: 'product-1',
    categoryId: 'category-1',
    brandId: 'brand-1',
    name: 'Leche entera',
    description: null,
    productType: 'GOODS',
    tracksInventory: true,
    tracksLots: true,
    tracksExpiration: true,
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };
  const productApi = { create: vi.fn(), update: vi.fn(), getById: vi.fn() };
  const brandApi = { list: vi.fn() };
  const categoryApi = { list: vi.fn() };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  afterEach(() => TestBed.resetTestingModule());

  it('enforces inventory, lot and expiration invariants', () => {
    expect(
      productTrackingError({
        productType: 'GOODS',
        tracksInventory: true,
        tracksLots: false,
        tracksExpiration: true,
      }),
    ).toBe('Un producto con caducidad debe controlar lotes.');
    expect(
      productTrackingError({
        productType: 'SERVICE',
        tracksInventory: true,
        tracksLots: false,
        tracksExpiration: false,
      }),
    ).toBe('Un servicio no puede controlar inventario, lotes o caducidad.');
  });

  it('creates a product and navigates to its detail', async () => {
    await setup(null);
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.setValue({
      categoryId: 'category-1',
      brandId: 'brand-1',
      name: ' Leche entera ',
      description: '',
      productType: 'GOODS',
      tracksInventory: true,
      tracksLots: true,
      tracksExpiration: true,
    });
    productApi.create.mockReturnValueOnce(of(product));

    component.submit();

    expect(productApi.create).toHaveBeenCalledWith({
      categoryId: 'category-1',
      brandId: 'brand-1',
      name: 'Leche entera',
      description: null,
      productType: 'GOODS',
      tracksInventory: true,
      tracksLots: true,
      tracksExpiration: true,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/catalog/products', product.id]);
  });

  it('updates an existing product', async () => {
    await setup(product.id);
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.patchValue({ name: 'Leche entera premium' });
    productApi.update.mockReturnValueOnce(of({ ...product, name: 'Leche entera premium' }));

    component.submit();

    expect(productApi.update).toHaveBeenCalledWith(
      product.id,
      expect.objectContaining({ name: 'Leche entera premium' }),
    );
  });

  it('preserves the form and shows correlation id when save fails', async () => {
    await setup(null);
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      submit(): void;
    };
    component.form.patchValue({ name: 'Leche entera' });
    productApi.create.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'El producto ya existe', correlationId: 'product-conflict-ref' },
          }),
      ),
    );

    component.submit();
    fixture.detectChanges();

    expect(component.form.value.name).toBe('Leche entera');
    expect(fixture.nativeElement.textContent).toContain(
      'El producto ya existe (Referencia: product-conflict-ref)',
    );
  });

  async function setup(productId: string | null): Promise<void> {
    vi.clearAllMocks();
    productApi.getById.mockReturnValue(of(product));
    brandApi.list.mockReturnValue(
      of([{ id: 'brand-1', code: 'MARCA', name: 'Marca', status: 'ACTIVE', createdAt: '' }]),
    );
    categoryApi.list.mockReturnValue(
      of([
        {
          id: 'category-1',
          parentCategoryId: null,
          code: 'LACTEOS',
          name: 'Lácteos',
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        { provide: ProductApi, useValue: productApi },
        { provide: BrandApi, useValue: brandApi },
        { provide: CategoryApi, useValue: categoryApi },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap(productId ? { productId } : {}) },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProductForm);
    fixture.detectChanges();
  }
});
