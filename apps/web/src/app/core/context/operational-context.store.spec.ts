import { TestBed } from '@angular/core/testing';

import { Branch, CashRegister, Warehouse } from '../../features/organization/models/organization.model';
import { OperationalContextStore } from './operational-context.store';

describe('OperationalContextStore', () => {
  let store: OperationalContextStore;
  const branches: Branch[] = [
    branch('branch-a', 'ACTIVE'),
    branch('branch-b', 'INACTIVE'),
  ];
  const warehouses: Warehouse[] = [
    warehouse('warehouse-a', 'branch-a', 'ACTIVE'),
    warehouse('warehouse-b', 'branch-b', 'ACTIVE'),
  ];
  const registers: CashRegister[] = [
    register('register-a', 'branch-a', 'ACTIVE'),
    register('register-b', 'branch-b', 'ACTIVE'),
  ];

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(OperationalContextStore);
  });

  afterEach(() => sessionStorage.clear());

  it('persists only selected identifiers and restores valid context', () => {
    store.revalidate(branches, warehouses, registers);
    store.selectBranch('branch-a');
    store.selectWarehouse('warehouse-a');
    store.selectCashRegister('register-a');

    expect(store.context()).toEqual({
      branchId: 'branch-a',
      warehouseId: 'warehouse-a',
      cashRegisterId: 'register-a',
    });
    expect(JSON.parse(sessionStorage.getItem('tienda.operational.context') ?? '{}')).toEqual({
      branchId: 'branch-a',
      warehouseId: 'warehouse-a',
      cashRegisterId: 'register-a',
    });
  });

  it('clears dependent resources when branch changes', () => {
    store.revalidate([branch('branch-a', 'ACTIVE'), branch('branch-c', 'ACTIVE')], warehouses, registers);
    store.selectBranch('branch-a');
    store.selectWarehouse('warehouse-a');
    store.selectCashRegister('register-a');

    store.selectBranch('branch-c');

    expect(store.context()).toEqual({ branchId: 'branch-c' });
  });

  it('rejects inactive branches and resources from another branch during revalidation', () => {
    sessionStorage.setItem(
      'tienda.operational.context',
      JSON.stringify({ branchId: 'branch-b', warehouseId: 'warehouse-b', cashRegisterId: 'register-b' }),
    );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    store = TestBed.inject(OperationalContextStore);

    store.revalidate(branches, warehouses, registers);

    expect(store.context()).toEqual({});
  });

  it('rejects an invalid warehouse selection', () => {
    store.revalidate(branches, warehouses, registers);
    store.selectBranch('branch-a');

    expect(() => store.selectWarehouse('warehouse-b')).toThrowError(
      'El almacén no pertenece a la sucursal activa.',
    );
  });

  function branch(id: string, status: 'ACTIVE' | 'INACTIVE'): Branch {
    return {
      branchId: id,
      code: id,
      name: id,
      legalName: id,
      timezone: 'America/Mexico_City',
      currencyCode: 'MXN',
      status,
      createdAt: '',
      updatedAt: '',
    };
  }

  function warehouse(
    id: string,
    branchId: string,
    status: 'ACTIVE' | 'INACTIVE',
  ): Warehouse {
    return {
      warehouseId: id,
      branchId,
      code: id,
      name: id,
      warehouseType: 'STORE',
      status,
      createdAt: '',
      updatedAt: '',
    };
  }

  function register(
    id: string,
    branchId: string,
    status: 'ACTIVE' | 'INACTIVE',
  ): CashRegister {
    return {
      cashRegisterId: id,
      branchId,
      deviceId: null,
      code: id,
      name: id,
      status,
      createdAt: '',
    };
  }
});
