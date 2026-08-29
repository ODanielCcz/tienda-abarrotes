import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { PurchasingOverview } from './purchasing-overview';

describe('PurchasingOverview', () => {
  let fixture: ComponentFixture<PurchasingOverview>;
  const api = { listSuppliers: vi.fn(), listPurchases: vi.fn() };
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', warehouseId: 'warehouse-1' })),
    selectedWarehouse: vi.fn(() => ({ warehouseId: 'warehouse-1', name: 'Almacén principal' })),
  };
  const session = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    api.listSuppliers.mockReturnValue(of([]));
    api.listPurchases.mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [PurchasingOverview],
      providers: [
        { provide: PurchasingApi, useValue: api },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: session },
        { provide: ActivatedRoute, useValue: {} },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(PurchasingOverview);
    fixture.detectChanges();
  });

  it('loads suppliers and purchases from the selected warehouse', () => {
    expect(api.listSuppliers).toHaveBeenCalledWith({});
    expect(api.listPurchases).toHaveBeenCalledWith({ warehouseId: 'warehouse-1' });
    expect(fixture.nativeElement.textContent).toContain('Almacén principal');
  });
});
