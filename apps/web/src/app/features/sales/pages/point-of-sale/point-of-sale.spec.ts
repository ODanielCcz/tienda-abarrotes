import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { CatalogReferenceApi } from '../../../catalog/products/data-access/catalog-reference-api';
import { CustomerApi } from '../../../customers/data-access/customer-api';
import { InventoryApi } from '../../../inventory/data-access/inventory-api';
import { SalesApi } from '../../data-access/sales-api';
import { PointOfSale } from './point-of-sale';

describe('PointOfSale', () => {
  let fixture: ComponentFixture<PointOfSale>;
  const salesApi = { create: vi.fn() };
  const inventoryApi = { listStock: vi.fn() };
  const catalogApi = { listPrices: vi.fn() };
  const customerApi = { list: vi.fn() };
  let router: Router;
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({ warehouseId: 'warehouse-1', name: 'Principal' })),
  };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    inventoryApi.listStock.mockReturnValue(
      of([stock('presentation-1', 'ARROZ-1KG', 12), stock('presentation-2', 'FRIJOL-1KG', 8)]),
    );
    catalogApi.listPrices.mockReturnValue(
      of([
        {
          priceId: 'price-1',
          productPresentationId: 'presentation-1',
          branchId: 'branch-1',
          amount: 25.5,
          currencyCode: 'MXN',
        },
      ]),
    );
    customerApi.list.mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [PointOfSale],
      providers: [
        provideRouter([]),
        { provide: SalesApi, useValue: salesApi },
        { provide: InventoryApi, useValue: inventoryApi },
        { provide: CatalogReferenceApi, useValue: catalogApi },
        { provide: CustomerApi, useValue: customerApi },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
      ],
    }).compileComponents();
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(PointOfSale);
    fixture.detectChanges();
  });

  it('offers only presentations with available stock and an effective price', () => {
    const component = expose(fixture);

    expect(inventoryApi.listStock).toHaveBeenCalledWith('warehouse-1');
    expect(catalogApi.listPrices).toHaveBeenCalledWith('branch-1');
    expect(component.catalog()).toEqual([
      expect.objectContaining({
        productPresentationId: 'presentation-1',
        unitPrice: 25.5,
        availableQuantity: 12,
      }),
    ]);
  });

  it('creates the order with the effective price and no client-side discount', () => {
    const component = expose(fixture);
    component.addToCart(component.catalog()[0]);
    component.changeQuantity('presentation-1', 2);
    salesApi.create.mockReturnValueOnce(of({ salesOrderId: 'order-1' }));

    component.submit();

    expect(salesApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        warehouseId: 'warehouse-1',
        channel: 'POS',
        currencyCode: 'MXN',
        idempotencyKey: component.idempotencyKey,
        items: [
          {
            productPresentationId: 'presentation-1',
            quantity: 2,
            unitPrice: 25.5,
            discountAmount: 0,
          },
        ],
      }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/sales', 'order-1']);
  });

  it('keeps the idempotency key when the backend rejects the order', () => {
    const component = expose(fixture);
    component.addToCart(component.catalog()[0]);
    const key = component.idempotencyKey;
    salesApi.create.mockReturnValueOnce(throwError(() => new Error('rejected')));

    component.submit();

    expect(component.idempotencyKey).toBe(key);
    expect(component.errorMessage()).toBeTruthy();
  });

  function expose(target: ComponentFixture<PointOfSale>) {
    return target.componentInstance as unknown as {
      catalog: () => Array<{
        productPresentationId: string;
        unitPrice: number;
        availableQuantity: number;
      }>;
      errorMessage: () => string | null;
      idempotencyKey: string;
      addToCart(item: unknown): void;
      changeQuantity(productPresentationId: string, quantity: number): void;
      submit(): void;
    };
  }

  function stock(productPresentationId: string, sku: string, availableQuantity: number) {
    return {
      stockBalanceId: `stock-${productPresentationId}`,
      warehouseId: 'warehouse-1',
      productPresentationId,
      sku,
      presentationName: 'Bolsa 1 kg',
      productName: sku.startsWith('ARROZ') ? 'Arroz' : 'Frijol',
      onHandQuantity: availableQuantity,
      reservedQuantity: 0,
      allocatedQuantity: 0,
      availableQuantity,
      averageUnitCost: 15,
      updatedAt: '',
    };
  }
});
