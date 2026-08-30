import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { filter, finalize, forkJoin, Observable, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import {
  OrganizationEntity,
  OrganizationEntityType,
  OrganizationFormDialog,
} from '../../components/organization-form-dialog/organization-form-dialog';
import { OrganizationStatusDialog } from '../../components/organization-status-dialog/organization-status-dialog';
import { OrganizationApi } from '../../data-access/organization-api';
import {
  ActiveStatus,
  Branch,
  CashRegister,
  CashRegisterStatus,
  Device,
  DeviceStatus,
  Warehouse,
} from '../../models/organization.model';

@Component({
  selector: 'app-organization-overview',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './organization-overview.html',
  styleUrl: './organization-overview.scss',
})
export class OrganizationOverview {
  private readonly api = inject(OrganizationApi);
  private readonly session = inject(AuthSessionStore);
  private readonly context = inject(OperationalContextStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly branches = signal<Branch[]>([]);
  protected readonly warehouses = signal<Warehouse[]>([]);
  protected readonly registers = signal<CashRegister[]>([]);
  protected readonly devices = signal<Device[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly branchColumns = ['code', 'name', 'timezone', 'currency', 'status', 'actions'];
  protected readonly warehouseColumns = ['code', 'name', 'branch', 'type', 'status', 'actions'];
  protected readonly registerColumns = ['code', 'name', 'branch', 'device', 'status', 'actions'];
  protected readonly deviceColumns = [
    'code',
    'type',
    'branch',
    'warehouse',
    'version',
    'status',
    'actions',
  ];

  protected readonly canReadBranches = this.can('ORGANIZATION_BRANCH_READ');
  protected readonly canReadWarehouses = this.can('ORGANIZATION_WAREHOUSE_READ');
  protected readonly canReadRegisters = this.can('ORGANIZATION_CASH_REGISTER_READ');
  protected readonly canReadDevices = this.can('ORGANIZATION_DEVICE_READ');

  constructor() {
    this.load();
  }

  protected can(permission: string): boolean {
    return this.session.hasPermission(permission);
  }

  protected open(type: OrganizationEntityType, entity: OrganizationEntity | null): void {
    this.dialog
      .open(OrganizationFormDialog, {
        width: '54rem',
        maxWidth: '95vw',
        disableClose: true,
        data: {
          type,
          entity,
          branches: this.branches(),
          warehouses: this.warehouses(),
          devices: this.devices(),
        },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  protected changeBranchStatus(branch: Branch): void {
    this.changeStatus(`Estado de ${branch.name}`, branch.status, ['ACTIVE', 'INACTIVE'], (status) =>
      this.api.changeBranchStatus(branch.branchId, status as ActiveStatus),
    );
  }

  protected changeWarehouseStatus(warehouse: Warehouse): void {
    this.changeStatus(
      `Estado de ${warehouse.name}`,
      warehouse.status,
      ['ACTIVE', 'INACTIVE'],
      (status) => this.api.changeWarehouseStatus(warehouse.warehouseId, status as ActiveStatus),
    );
  }

  protected changeRegisterStatus(register: CashRegister): void {
    this.changeStatus(
      `Estado de ${register.name}`,
      register.status,
      ['ACTIVE', 'INACTIVE', 'MAINTENANCE'],
      (status) =>
        this.api.changeCashRegisterStatus(register.cashRegisterId, status as CashRegisterStatus),
    );
  }

  protected changeDeviceStatus(device: Device): void {
    this.changeStatus(
      `Estado de ${device.deviceCode}`,
      device.status,
      ['ACTIVE', 'BLOCKED', 'RETIRED'],
      (status) => this.api.changeDeviceStatus(device.deviceId, status as DeviceStatus),
    );
  }

  protected branchName(branchId: string): string {
    return this.branches().find((branch) => branch.branchId === branchId)?.name ?? branchId;
  }

  protected warehouseName(warehouseId: string | null): string {
    if (!warehouseId) return '—';
    return (
      this.warehouses().find((warehouse) => warehouse.warehouseId === warehouseId)?.name ??
      warehouseId
    );
  }

  protected deviceCode(deviceId: string | null): string {
    if (!deviceId) return '—';
    return this.devices().find((device) => device.deviceId === deviceId)?.deviceCode ?? deviceId;
  }

  protected refresh(): void {
    this.load();
  }

  private changeStatus(
    title: string,
    currentStatus: string,
    options: string[],
    operation: (status: string) => Observable<unknown>,
  ): void {
    this.dialog
      .open(OrganizationStatusDialog, {
        width: '28rem',
        data: { title, currentStatus, options },
      })
      .afterClosed()
      .pipe(
        filter((status): status is string => Boolean(status) && status !== currentStatus),
        switchMap(operation),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: () => this.load(), error: (error) => this.showError(error) });
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      branches: this.canReadBranches ? this.api.listBranches() : of([]),
      warehouses: this.canReadWarehouses ? this.api.listWarehouses() : of([]),
      registers: this.canReadRegisters ? this.api.listCashRegisters() : of([]),
      devices: this.canReadDevices ? this.api.listDevices() : of([]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ branches, warehouses, registers, devices }) => {
          this.branches.set(branches);
          this.warehouses.set(warehouses);
          this.registers.set(registers);
          this.devices.set(devices);
          if (this.canReadBranches && this.canReadWarehouses && this.canReadRegisters) {
            this.context.revalidate(branches, warehouses, registers);
          }
        },
        error: (error) => this.showError(error),
      });
  }

  private showError(error: unknown): void {
    this.loading.set(false);
    this.errorMessage.set(apiErrorMessage(error, 'No fue posible completar la operación.'));
  }
}
