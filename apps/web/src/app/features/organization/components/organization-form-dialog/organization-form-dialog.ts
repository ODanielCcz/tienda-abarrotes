import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, Observable } from 'rxjs';

import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { OrganizationApi } from '../../data-access/organization-api';
import {
  Branch,
  BranchRequest,
  CashRegister,
  CashRegisterRequest,
  Device,
  DeviceRequest,
  DeviceType,
  Warehouse,
  WarehouseRequest,
  WarehouseType,
} from '../../models/organization.model';

export type OrganizationEntityType = 'BRANCH' | 'WAREHOUSE' | 'CASH_REGISTER' | 'DEVICE';
export type OrganizationEntity = Branch | Warehouse | CashRegister | Device;

export interface OrganizationFormDialogData {
  type: OrganizationEntityType;
  entity: OrganizationEntity | null;
  branches: Branch[];
  warehouses: Warehouse[];
  devices: Device[];
}

@Component({
  selector: 'app-organization-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './organization-form-dialog.html',
  styleUrl: './organization-form-dialog.scss',
})
export class OrganizationFormDialog {
  private readonly api = inject(OrganizationApi);
  private readonly ref = inject(MatDialogRef<OrganizationFormDialog, OrganizationEntity | null>);
  protected readonly data = inject<OrganizationFormDialogData>(MAT_DIALOG_DATA);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly warehouseTypes: WarehouseType[] = [
    'STORE',
    'BACKROOM',
    'TRANSIT',
    'DAMAGED',
    'VIRTUAL',
  ];
  protected readonly deviceTypes: DeviceType[] = [
    'POS',
    'MOBILE_EMPLOYEE',
    'MOBILE_CUSTOMER',
    'WEB',
  ];
  protected readonly form = new FormGroup({
    branchId: new FormControl(this.branchId(), {
      nonNullable: true,
      validators: Validators.required,
    }),
    code: new FormControl(this.code(), {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(80)],
    }),
    name: new FormControl(this.name(), {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(150)],
    }),
    legalName: new FormControl(this.branch()?.legalName ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(200),
    }),
    timezone: new FormControl(this.branch()?.timezone ?? 'America/Mexico_City', {
      nonNullable: true,
    }),
    currencyCode: new FormControl(this.branch()?.currencyCode ?? 'MXN', {
      nonNullable: true,
      validators: Validators.maxLength(3),
    }),
    warehouseType: new FormControl<WarehouseType>(this.warehouse()?.warehouseType ?? 'STORE', {
      nonNullable: true,
    }),
    deviceId: new FormControl<string | null>(this.register()?.deviceId ?? null),
    warehouseId: new FormControl<string | null>(this.device()?.warehouseId ?? null),
    deviceType: new FormControl<DeviceType>(this.device()?.deviceType ?? 'WEB', {
      nonNullable: true,
    }),
    platform: new FormControl(this.device()?.platform ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(50),
    }),
    appVersion: new FormControl(this.device()?.appVersion ?? '', {
      nonNullable: true,
      validators: Validators.maxLength(50),
    }),
  });

  protected title(): string {
    const label =
      this.data.type === 'BRANCH'
        ? 'sucursal'
        : this.data.type === 'WAREHOUSE'
          ? 'almacén'
          : this.data.type === 'CASH_REGISTER'
            ? 'caja registradora'
            : 'dispositivo';
    return `${this.data.entity ? 'Editar' : 'Nueva'} ${label}`;
  }

  protected warehousesForBranch(): Warehouse[] {
    return this.data.warehouses.filter(
      (warehouse) => warehouse.branchId === this.form.controls.branchId.value,
    );
  }

  protected devicesForBranch(): Device[] {
    return this.data.devices.filter(
      (device) => device.branchId === this.form.controls.branchId.value,
    );
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.operation()
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (entity) => this.ref.close(entity),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el recurso.')),
      });
  }

  private operation(): Observable<OrganizationEntity> {
    const value = this.form.getRawValue();
    if (this.data.type === 'BRANCH') {
      const request: BranchRequest = {
        code: value.code.trim().toUpperCase(),
        name: value.name.trim(),
        legalName: this.optional(value.legalName),
        timezone: this.optional(value.timezone),
        currencyCode: this.optional(value.currencyCode)?.toUpperCase() ?? null,
      };
      return this.data.entity
        ? this.api.updateBranch((this.data.entity as Branch).branchId, request)
        : this.api.createBranch(request);
    }
    if (this.data.type === 'WAREHOUSE') {
      const request: WarehouseRequest = {
        branchId: value.branchId,
        code: value.code.trim().toUpperCase(),
        name: value.name.trim(),
        warehouseType: value.warehouseType,
      };
      return this.data.entity
        ? this.api.updateWarehouse((this.data.entity as Warehouse).warehouseId, request)
        : this.api.createWarehouse(request);
    }
    if (this.data.type === 'CASH_REGISTER') {
      const request: CashRegisterRequest = {
        branchId: value.branchId,
        deviceId: value.deviceId,
        code: value.code.trim().toUpperCase(),
        name: value.name.trim(),
      };
      return this.data.entity
        ? this.api.updateCashRegister((this.data.entity as CashRegister).cashRegisterId, request)
        : this.api.createCashRegister(request);
    }
    const request: DeviceRequest = {
      branchId: value.branchId,
      warehouseId: value.warehouseId,
      deviceCode: value.code.trim().toUpperCase(),
      deviceType: value.deviceType,
      platform: this.optional(value.platform),
      appVersion: this.optional(value.appVersion),
    };
    return this.data.entity
      ? this.api.updateDevice((this.data.entity as Device).deviceId, request)
      : this.api.createDevice(request);
  }

  private branch(): Branch | null {
    return this.data.type === 'BRANCH' ? (this.data.entity as Branch | null) : null;
  }

  private warehouse(): Warehouse | null {
    return this.data.type === 'WAREHOUSE' ? (this.data.entity as Warehouse | null) : null;
  }

  private register(): CashRegister | null {
    return this.data.type === 'CASH_REGISTER' ? (this.data.entity as CashRegister | null) : null;
  }

  private device(): Device | null {
    return this.data.type === 'DEVICE' ? (this.data.entity as Device | null) : null;
  }

  private branchId(): string {
    if (this.data.type === 'WAREHOUSE') return this.warehouse()?.branchId ?? '';
    if (this.data.type === 'CASH_REGISTER') return this.register()?.branchId ?? '';
    if (this.data.type === 'DEVICE') return this.device()?.branchId ?? '';
    return '';
  }

  private code(): string {
    if (this.data.type === 'BRANCH') return this.branch()?.code ?? '';
    if (this.data.type === 'WAREHOUSE') return this.warehouse()?.code ?? '';
    if (this.data.type === 'CASH_REGISTER') return this.register()?.code ?? '';
    return this.device()?.deviceCode ?? '';
  }

  private name(): string {
    if (this.data.type === 'BRANCH') return this.branch()?.name ?? '';
    if (this.data.type === 'WAREHOUSE') return this.warehouse()?.name ?? '';
    if (this.data.type === 'CASH_REGISTER') return this.register()?.name ?? '';
    return this.device()?.deviceCode ?? '';
  }

  private optional(value: string): string | null {
    return value.trim() || null;
  }
}
