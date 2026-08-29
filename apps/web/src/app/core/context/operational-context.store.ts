import { computed, Injectable, signal } from '@angular/core';

import {
  Branch,
  CashRegister,
  Warehouse,
} from '../../features/organization/models/organization.model';

const OPERATIONAL_CONTEXT_KEY = 'tienda.operational.context';

export interface OperationalContext {
  branchId?: string;
  warehouseId?: string;
  cashRegisterId?: string;
}

@Injectable({ providedIn: 'root' })
export class OperationalContextStore {
  private readonly contextState = signal<OperationalContext>(this.restore());
  private readonly branchesState = signal<Branch[]>([]);
  private readonly warehousesState = signal<Warehouse[]>([]);
  private readonly cashRegistersState = signal<CashRegister[]>([]);

  readonly context = this.contextState.asReadonly();
  readonly branches = this.branchesState.asReadonly();
  readonly warehouses = computed(() => {
    const branchId = this.contextState().branchId;
    return branchId === undefined
      ? []
      : this.warehousesState().filter((warehouse) => warehouse.branchId === branchId);
  });
  readonly cashRegisters = computed(() => {
    const branchId = this.contextState().branchId;
    return branchId === undefined
      ? []
      : this.cashRegistersState().filter((register) => register.branchId === branchId);
  });
  readonly selectedBranch = computed(() =>
    this.branchesState().find((branch) => branch.branchId === this.contextState().branchId),
  );
  readonly selectedWarehouse = computed(() =>
    this.warehousesState().find(
      (warehouse) => warehouse.warehouseId === this.contextState().warehouseId,
    ),
  );
  readonly selectedCashRegister = computed(() =>
    this.cashRegistersState().find(
      (register) => register.cashRegisterId === this.contextState().cashRegisterId,
    ),
  );

  revalidate(
    branches: Branch[],
    warehouses: Warehouse[],
    cashRegisters: CashRegister[],
  ): void {
    const activeBranches = branches.filter((branch) => branch.status === 'ACTIVE');
    const activeBranchIds = new Set(activeBranches.map((branch) => branch.branchId));
    const activeWarehouses = warehouses.filter(
      (warehouse) =>
        warehouse.status === 'ACTIVE' && activeBranchIds.has(warehouse.branchId),
    );
    const activeRegisters = cashRegisters.filter(
      (register) => register.status === 'ACTIVE' && activeBranchIds.has(register.branchId),
    );

    this.branchesState.set(activeBranches);
    this.warehousesState.set(activeWarehouses);
    this.cashRegistersState.set(activeRegisters);

    const current = this.contextState();
    const branch = activeBranches.find((candidate) => candidate.branchId === current.branchId);
    if (branch === undefined) {
      this.update({});
      return;
    }

    const warehouse = activeWarehouses.find(
      (candidate) =>
        candidate.warehouseId === current.warehouseId &&
        candidate.branchId === branch.branchId,
    );
    const register = activeRegisters.find(
      (candidate) =>
        candidate.cashRegisterId === current.cashRegisterId &&
        candidate.branchId === branch.branchId,
    );

    this.update({
      branchId: branch.branchId,
      warehouseId: warehouse?.warehouseId,
      cashRegisterId: register?.cashRegisterId,
    });
  }

  selectBranch(branchId: string | null): void {
    if (branchId === null) {
      this.update({});
      return;
    }
    if (!this.branchesState().some((branch) => branch.branchId === branchId)) {
      throw new Error('La sucursal seleccionada no está activa o no es accesible.');
    }
    const current = this.contextState();
    if (current.branchId === branchId) return;
    this.update({ branchId });
  }

  selectWarehouse(warehouseId: string | null): void {
    if (warehouseId === null) {
      const { branchId, cashRegisterId } = this.contextState();
      this.update({ branchId, cashRegisterId });
      return;
    }
    const branchId = this.contextState().branchId;
    const valid = this.warehousesState().some(
      (warehouse) =>
        warehouse.warehouseId === warehouseId && warehouse.branchId === branchId,
    );
    if (!valid) throw new Error('El almacén no pertenece a la sucursal activa.');
    this.update({ ...this.contextState(), warehouseId });
  }

  selectCashRegister(cashRegisterId: string | null): void {
    if (cashRegisterId === null) {
      const { branchId, warehouseId } = this.contextState();
      this.update({ branchId, warehouseId });
      return;
    }
    const branchId = this.contextState().branchId;
    const valid = this.cashRegistersState().some(
      (register) =>
        register.cashRegisterId === cashRegisterId && register.branchId === branchId,
    );
    if (!valid) throw new Error('La caja no pertenece a la sucursal activa.');
    this.update({ ...this.contextState(), cashRegisterId });
  }

  clear(): void {
    this.contextState.set({});
    this.branchesState.set([]);
    this.warehousesState.set([]);
    this.cashRegistersState.set([]);
    sessionStorage.removeItem(OPERATIONAL_CONTEXT_KEY);
  }

  private update(context: OperationalContext): void {
    const normalized: OperationalContext = {
      ...(context.branchId ? { branchId: context.branchId } : {}),
      ...(context.warehouseId ? { warehouseId: context.warehouseId } : {}),
      ...(context.cashRegisterId ? { cashRegisterId: context.cashRegisterId } : {}),
    };
    this.contextState.set(normalized);
    sessionStorage.setItem(OPERATIONAL_CONTEXT_KEY, JSON.stringify(normalized));
  }

  private restore(): OperationalContext {
    const stored = sessionStorage.getItem(OPERATIONAL_CONTEXT_KEY);
    if (stored === null) return {};
    try {
      const parsed = JSON.parse(stored) as Record<string, unknown>;
      return {
        ...(typeof parsed['branchId'] === 'string' ? { branchId: parsed['branchId'] } : {}),
        ...(typeof parsed['warehouseId'] === 'string'
          ? { warehouseId: parsed['warehouseId'] }
          : {}),
        ...(typeof parsed['cashRegisterId'] === 'string'
          ? { cashRegisterId: parsed['cashRegisterId'] }
          : {}),
      };
    } catch {
      sessionStorage.removeItem(OPERATIONAL_CONTEXT_KEY);
      return {};
    }
  }
}
