import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormArray, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { ProductApi } from '../../../catalog/products/data-access/product-api';
import { InventoryApi } from '../../data-access/inventory-api';
import { InventoryReceiptForm } from './inventory-receipt-form';

describe('InventoryReceiptForm', () => {
  let fixture: ComponentFixture<InventoryReceiptForm>;
  const inventoryApi = { createReceipt: vi.fn() };
  const productApi = { list: vi.fn(), listPresentations: vi.fn() };
  const contextStore = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({
      warehouseId: 'warehouse-1',
      branchId: 'branch-1',
      code: 'MAIN',
      name: 'Almacén principal',
      status: 'ACTIVE',
    })),
  };
  const router = { navigate: vi.fn().mockResolvedValue(true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    productApi.list.mockReturnValue(
      of({
        content: [
          {
            id: 'product-1',
            categoryId: null,
            brandId: null,
            name: 'Arroz',
            description: null,
            productType: 'GOODS',
            tracksInventory: true,
            tracksLots: true,
            tracksExpiration: true,
            status: 'ACTIVE',
            createdAt: '',
            updatedAt: '',
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
          unitId: 'unit-1',
          taxId: null,
          sku: 'ARROZ-1KG',
          name: 'Bolsa 1 kg',
          conversionFactor: 1,
          netContent: 1000,
          minimumStock: 10,
          status: 'ACTIVE',
          createdAt: '',
          updatedAt: '',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [InventoryReceiptForm],
      providers: [
        { provide: InventoryApi, useValue: inventoryApi },
        { provide: ProductApi, useValue: productApi },
        { provide: OperationalContextStore, useValue: contextStore },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryReceiptForm);
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('submits a simple receipt using the selected warehouse', () => {
    const component = fixture.componentInstance as unknown as {
      form: FormGroup;
      items: FormArray;
      loadPresentations(productId: string): void;
      submit(): void;
      idempotencyKey: string;
    };
    component.loadPresentations('product-1');
    component.items.at(0).patchValue({
      productId: 'product-1',
      productPresentationId: 'presentation-1',
      lotNumber: 'LOT-001',
      manufacturedAt: '2026-08-01',
      expiresAt: '2027-08-01',
      quantity: 10,
      unitCost: 18.5,
    });
    inventoryApi.createReceipt.mockReturnValueOnce(
      of({
        receiptId: 'receipt-1',
        warehouseId: 'warehouse-1',
        supplierId: null,
        status: 'CONFIRMED',
        receivedAt: '',
        items: [],
        pallets: [],
      }),
    );

    component.submit();

    expect(inventoryApi.createReceipt).toHaveBeenCalledWith(
      expect.objectContaining({
        warehouseId: 'warehouse-1',
        idempotencyKey: component.idempotencyKey,
        items: [
          expect.objectContaining({
            productPresentationId: 'presentation-1',
            quantity: 10,
          }),
        ],
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/inventory']);
  });

  it('preserves the form and idempotency key after an API error', () => {
    const component = fixture.componentInstance as unknown as {
      items: FormArray;
      submit(): void;
      idempotencyKey: string;
    };
    const originalKey = component.idempotencyKey;
    component.items.at(0).patchValue({
      productId: 'product-1',
      productPresentationId: 'presentation-1',
      lotNumber: 'LOT-ERROR-001',
      expiresAt: '2027-08-01',
      quantity: 5,
      unitCost: 12,
    });
    inventoryApi.createReceipt.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              message: 'La clave pertenece a otra recepción',
              correlationId: 'receipt-conflict-ref',
            },
          }),
      ),
    );

    component.submit();
    fixture.detectChanges();

    expect(component.idempotencyKey).toBe(originalKey);
    expect(component.items.at(0).value.quantity).toBe(5);
    expect(fixture.nativeElement.textContent).toContain('receipt-conflict-ref');
  });
});
