import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { InventoryApi } from '../../data-access/inventory-api';
import { InventoryOverview } from './inventory-overview';

describe('InventoryOverview', () => {
  let fixture: ComponentFixture<InventoryOverview>;
  const api = {
    listStock: vi.fn(),
    listLots: vi.fn(),
    listPallets: vi.fn(),
    listMovements: vi.fn(),
  };
  const contextStore = {
    context: () => ({ branchId: 'branch-a', warehouseId: 'warehouse-a' }),
    selectedWarehouse: () => ({ warehouseId: 'warehouse-a', name: 'Almacén principal' }),
  };
  const sessionStore = { hasPermission: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    sessionStore.hasPermission.mockReturnValue(true);
    api.listStock.mockReturnValue(
      of([
        {
          stockBalanceId: 'stock-1',
          warehouseId: 'warehouse-a',
          productPresentationId: 'presentation-1',
          sku: 'ARROZ-1KG',
          presentationName: 'Bolsa 1 kg',
          productName: 'Arroz',
          onHandQuantity: 25,
          reservedQuantity: 2,
          allocatedQuantity: 1,
          availableQuantity: 22,
          averageUnitCost: 18.5,
          updatedAt: '2026-08-29T00:00:00Z',
        },
      ]),
    );
    api.listLots.mockReturnValue(
      of([
        {
          lotId: 'lot-1',
          productPresentationId: 'presentation-1',
          supplierId: null,
          lotNumber: 'LOT-ARROZ-001',
          manufacturedAt: '2026-08-01',
          expiresAt: '2027-08-01',
          status: 'ACTIVE',
          warehouseId: 'warehouse-a',
          onHandQuantity: 25,
          availableQuantity: 22,
          createdAt: '',
        },
      ]),
    );
    api.listPallets.mockReturnValue(
      of([
        {
          palletId: 'pallet-1',
          warehouseId: 'warehouse-a',
          stockMovementId: 'movement-1',
          palletCode: 'PAL-001',
          externalPalletCode: 'SSCC-001',
          status: 'RECEIVED',
          receivedAt: '',
          items: [],
        },
      ]),
    );
    api.listMovements.mockReturnValue(
      of([
        {
          stockMovementId: 'movement-1',
          branchId: 'branch-a',
          warehouseId: 'warehouse-a',
          movementType: 'PURCHASE_RECEIPT',
          status: 'CONFIRMED',
          sourceType: 'PURCHASE',
          sourceId: null,
          reason: null,
          idempotencyKey: null,
          createdAt: '2026-08-29T00:00:00Z',
          confirmedAt: '2026-08-29T00:00:00Z',
          items: [],
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [InventoryOverview],
      providers: [
        { provide: InventoryApi, useValue: api },
        { provide: OperationalContextStore, useValue: contextStore },
        { provide: AuthSessionStore, useValue: sessionStore },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InventoryOverview);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads all permitted inventory views for the selected warehouse', () => {
    expect(api.listStock).toHaveBeenCalledWith('warehouse-a');
    expect(api.listLots).toHaveBeenCalledWith({ warehouseId: 'warehouse-a' });
    expect(api.listPallets).toHaveBeenCalledWith('warehouse-a');
    expect(api.listMovements).toHaveBeenCalledWith({ warehouseId: 'warehouse-a' });
    expect(fixture.nativeElement.textContent).toContain('Almacén principal');
    expect(fixture.nativeElement.textContent).toContain('ARROZ-1KG');
    const component = fixture.componentInstance as unknown as {
      lots: () => Array<{ lotNumber: string }>;
      pallets: () => Array<{ palletCode: string }>;
      movements: () => Array<{ movementType: string }>;
    };
    expect(component.lots()[0].lotNumber).toBe('LOT-ARROZ-001');
    expect(component.pallets()[0].palletCode).toBe('PAL-001');
    expect(component.movements()[0].movementType).toBe('PURCHASE_RECEIPT');
  });

  it('shows a correlation reference without replacing the selected context', async () => {
    api.listStock.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { message: 'Error de inventario', correlationId: 'inventory-ref-1' },
          }),
      ),
    );
    const component = fixture.componentInstance as unknown as { refresh(): void };

    component.refresh();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Error de inventario (Referencia: inventory-ref-1)',
    );
    expect(contextStore.context().warehouseId).toBe('warehouse-a');
  });
});
