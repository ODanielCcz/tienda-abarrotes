import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../core/context/operational-context.store';
import { OrganizationApi } from '../../../features/organization/data-access/organization-api';
import { OperationalContextSelector } from './operational-context-selector';

describe('OperationalContextSelector', () => {
  let fixture: ComponentFixture<OperationalContextSelector>;
  const organizationApi = {
    listBranches: vi.fn(),
    listWarehouses: vi.fn(),
    listCashRegisters: vi.fn(),
  };
  const contextStore = {
    context: vi.fn(),
    branches: vi.fn(),
    warehouses: vi.fn(),
    cashRegisters: vi.fn(),
    revalidate: vi.fn(),
    selectBranch: vi.fn(),
    selectWarehouse: vi.fn(),
    selectCashRegister: vi.fn(),
  };
  const sessionStore = { hasPermission: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    sessionStore.hasPermission.mockReturnValue(true);
    organizationApi.listBranches.mockReturnValue(of([{ branchId: 'branch-a', name: 'Centro' }]));
    organizationApi.listWarehouses.mockReturnValue(
      of([{ warehouseId: 'warehouse-a', branchId: 'branch-a', name: 'Principal' }]),
    );
    organizationApi.listCashRegisters.mockReturnValue(
      of([{ cashRegisterId: 'register-a', branchId: 'branch-a', name: 'Caja 1' }]),
    );
    contextStore.context.mockReturnValue({ branchId: 'branch-a' });
    contextStore.branches.mockReturnValue([{ branchId: 'branch-a', name: 'Centro' }]);
    contextStore.warehouses.mockReturnValue([
      { warehouseId: 'warehouse-a', branchId: 'branch-a', name: 'Principal' },
    ]);
    contextStore.cashRegisters.mockReturnValue([
      { cashRegisterId: 'register-a', branchId: 'branch-a', name: 'Caja 1' },
    ]);

    await TestBed.configureTestingModule({
      imports: [OperationalContextSelector],
      providers: [
        { provide: OrganizationApi, useValue: organizationApi },
        { provide: OperationalContextStore, useValue: contextStore },
        { provide: AuthSessionStore, useValue: sessionStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OperationalContextSelector);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads and revalidates only active accessible resources', () => {
    expect(organizationApi.listBranches).toHaveBeenCalledWith('ACTIVE');
    expect(organizationApi.listWarehouses).toHaveBeenCalledWith(undefined, 'ACTIVE');
    expect(organizationApi.listCashRegisters).toHaveBeenCalledWith(undefined, 'ACTIVE');
    expect(contextStore.revalidate).toHaveBeenCalledWith(
      expect.any(Array),
      expect.any(Array),
      expect.any(Array),
    );
    expect(fixture.nativeElement.querySelectorAll('mat-select')).toHaveLength(3);
  });

  it('updates selections through the validated store', () => {
    const component = fixture.componentInstance as unknown as {
      branchChanged(value: string | null): void;
      warehouseChanged(value: string | null): void;
      cashRegisterChanged(value: string | null): void;
    };

    component.branchChanged('branch-a');
    component.warehouseChanged('warehouse-a');
    component.cashRegisterChanged('register-a');

    expect(contextStore.selectBranch).toHaveBeenCalledWith('branch-a');
    expect(contextStore.selectWarehouse).toHaveBeenCalledWith('warehouse-a');
    expect(contextStore.selectCashRegister).toHaveBeenCalledWith('register-a');
  });
});
