import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { InventoryApi } from '../../data-access/inventory-api';
import { AdvancedInventory } from './advanced-inventory';

describe('AdvancedInventory', () => {
  let fixture: ComponentFixture<AdvancedInventory>;
  const api = {
    listStock: vi.fn(),
    listLots: vi.fn(),
    listExpiringLots: vi.fn(),
    adjust: vi.fn(),
    transfer: vi.fn(),
    createCount: vi.fn(),
    confirmCount: vi.fn(),
    reserve: vi.fn(),
    releaseReservation: vi.fn(),
  };
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({
      warehouseId: 'warehouse-1',
      branchId: 'branch-1',
      name: 'Principal',
    })),
    warehouses: vi.fn(() => [
      { warehouseId: 'warehouse-1', branchId: 'branch-1', name: 'Principal', status: 'ACTIVE' },
      { warehouseId: 'warehouse-2', branchId: 'branch-1', name: 'Secundario', status: 'ACTIVE' },
    ]),
  };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    const stock = [
      {
        stockBalanceId: 'stock-1',
        warehouseId: 'warehouse-1',
        productPresentationId: 'presentation-1',
        sku: 'ARROZ',
        presentationName: 'Bolsa',
        productName: 'Arroz',
        onHandQuantity: 10,
        reservedQuantity: 0,
        allocatedQuantity: 0,
        availableQuantity: 10,
        averageUnitCost: 20,
        updatedAt: '',
      },
    ];
    api.listStock.mockReturnValue(of(stock));
    api.listLots.mockReturnValue(of([]));
    api.listExpiringLots.mockReturnValue(of([]));
    api.adjust.mockReturnValue(of({ stockMovementId: 'movement-1' }));
    await TestBed.configureTestingModule({
      imports: [AdvancedInventory],
      providers: [
        { provide: InventoryApi, useValue: api },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdvancedInventory);
    fixture.detectChanges();
  });

  it('creates an adjustment in the selected warehouse and reloads server data', () => {
    const component = fixture.componentInstance as unknown as {
      adjustmentForm: FormGroup;
      adjust(): void;
    };
    component.adjustmentForm.setValue({
      productPresentationId: 'presentation-1',
      lotId: null,
      direction: 'IN',
      quantity: 2,
      unitCost: 20,
      reason: 'Corrección',
    });
    api.listStock.mockClear();

    component.adjust();

    expect(api.adjust).toHaveBeenCalledWith({
      warehouseId: 'warehouse-1',
      reason: 'Corrección',
      items: [
        {
          productPresentationId: 'presentation-1',
          lotId: null,
          direction: 'IN',
          quantity: 2,
          unitCost: 20,
        },
      ],
    });
    expect(api.listStock).toHaveBeenCalledWith('warehouse-1');
  });

  it('transfers stock to another active warehouse', () => {
    const component = fixture.componentInstance as unknown as {
      transferForm: FormGroup;
      transfer(): void;
    };
    api.transfer.mockReturnValueOnce(of([]));
    component.transferForm.setValue({
      toWarehouseId: 'warehouse-2',
      productPresentationId: 'presentation-1',
      lotId: null,
      quantity: 3,
      unitCost: 20,
      reason: 'Reabasto',
    });

    component.transfer();

    expect(api.transfer).toHaveBeenCalledWith(
      expect.objectContaining({
        fromWarehouseId: 'warehouse-1',
        toWarehouseId: 'warehouse-2',
      }),
    );
  });

  it('creates and confirms a physical count', () => {
    const component = fixture.componentInstance as unknown as {
      countForm: FormGroup;
      createCount(): void;
      confirmCount(): void;
    };
    const count = {
      inventoryCountId: 'count-1',
      warehouseId: 'warehouse-1',
      status: 'OPEN',
      startedBy: 'user-1',
      confirmedBy: null,
      startedAt: '',
      confirmedAt: null,
      items: [],
    };
    api.createCount.mockReturnValueOnce(of(count));
    api.confirmCount.mockReturnValueOnce(of({ ...count, status: 'CONFIRMED' }));
    component.countForm.setValue({
      productPresentationId: 'presentation-1',
      lotId: null,
      countedQuantity: 9,
    });

    component.createCount();
    component.confirmCount();

    expect(api.createCount).toHaveBeenCalled();
    expect(api.confirmCount).toHaveBeenCalledWith('count-1');
  });

  it('rotates idempotency after creating a reservation and can release it', () => {
    const component = fixture.componentInstance as unknown as {
      reservationForm: FormGroup;
      reservationIdempotencyKey: string;
      reserve(): void;
      releaseReservation(): void;
    };
    const originalKey = component.reservationIdempotencyKey;
    const reservation = {
      reservationId: 'reservation-1',
      branchId: 'branch-1',
      customerId: null,
      sourceType: 'MANUAL',
      sourceId: 'source-1',
      status: 'ACTIVE',
      idempotencyKey: originalKey,
      expiresAt: '',
      createdAt: '',
      updatedAt: '',
      items: [],
    };
    api.reserve.mockReturnValueOnce(of(reservation));
    api.releaseReservation.mockReturnValueOnce(of({ ...reservation, status: 'CANCELLED' }));
    component.reservationForm.patchValue({
      productPresentationId: 'presentation-1',
      quantity: 2,
    });

    component.reserve();
    component.releaseReservation();

    expect(api.reserve).toHaveBeenCalledWith(
      expect.objectContaining({ idempotencyKey: originalKey }),
    );
    expect(component.reservationIdempotencyKey).not.toBe(originalKey);
    expect(api.releaseReservation).toHaveBeenCalledWith('reservation-1');
  });
});
