import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { ProductApi } from '../../../catalog/products/data-access/product-api';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { PurchaseForm } from './purchase-form';

describe('PurchaseForm', () => {
  let fixture: ComponentFixture<PurchaseForm>;
  const api = { listSuppliers: vi.fn(), createPurchase: vi.fn() };
  const productApi = { list: vi.fn(), listPresentations: vi.fn() };
  const context = {
    context: vi.fn(() => ({ warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({ name: 'Principal' })),
  };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    api.listSuppliers.mockReturnValue(
      of([
        {
          supplierId: 'supplier-1',
          supplierCode: 'P1',
          legalName: 'Proveedor',
          tradeName: null,
          taxId: null,
          email: null,
          phone: null,
          creditDays: 0,
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );
    productApi.list.mockReturnValue(
      of({
        content: [
          {
            id: 'product-1',
            name: 'Arroz',
            productType: 'GOODS',
            tracksInventory: true,
            tracksLots: false,
            tracksExpiration: false,
            status: 'ACTIVE',
          },
        ],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }),
    );
    productApi.listPresentations.mockReturnValue(
      of([
        {
          id: 'presentation-1',
          productId: 'product-1',
          sku: 'ARROZ',
          name: 'Bolsa',
          status: 'ACTIVE',
        },
      ]),
    );
    await TestBed.configureTestingModule({
      imports: [PurchaseForm],
      providers: [
        { provide: PurchasingApi, useValue: api },
        { provide: ProductApi, useValue: productApi },
        { provide: OperationalContextStore, useValue: context },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PurchaseForm);
    fixture.detectChanges();
  });

  it('creates a draft purchase using the selected warehouse', () => {
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      items: FormArray;
      loadPresentations(id: string): void;
      submit(): void;
      idempotencyKey: string;
    };
    component.form.patchValue({
      supplierId: 'supplier-1',
      supplierDocument: 'FAC-1',
      currencyCode: 'MXN',
    });
    component.loadPresentations('product-1');
    component.items
      .at(0)
      .patchValue({
        productId: 'product-1',
        productPresentationId: 'presentation-1',
        quantity: 10,
        unitCost: 20,
        discountAmount: 0,
        taxAmount: 32,
      });
    api.createPurchase.mockReturnValueOnce(of({ purchaseId: 'purchase-1' }));

    component.submit();

    expect(api.createPurchase).toHaveBeenCalledWith(
      expect.objectContaining({
        warehouseId: 'warehouse-1',
        supplierId: 'supplier-1',
        idempotencyKey: component.idempotencyKey,
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/purchasing/purchases', 'purchase-1']);
  });
});
