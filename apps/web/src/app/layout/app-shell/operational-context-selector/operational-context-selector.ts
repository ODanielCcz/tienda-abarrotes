import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin, of } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../core/context/operational-context.store';
import { OrganizationApi } from '../../../features/organization/data-access/organization-api';
import { apiErrorMessage } from '../../../shared/utilities/api-error-message';

@Component({
  selector: 'app-operational-context-selector',
  imports: [MatFormFieldModule, MatIconModule, MatProgressSpinnerModule, MatSelectModule],
  templateUrl: './operational-context-selector.html',
  styleUrl: './operational-context-selector.scss',
})
export class OperationalContextSelector {
  private readonly organizationApi = inject(OrganizationApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly context = this.contextStore.context;
  protected readonly branches = this.contextStore.branches;
  protected readonly warehouses = this.contextStore.warehouses;
  protected readonly cashRegisters = this.contextStore.cashRegisters;
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly canReadBranches = this.sessionStore.hasPermission(
    'ORGANIZATION_BRANCH_READ',
  );
  protected readonly canReadWarehouses = this.sessionStore.hasPermission(
    'ORGANIZATION_WAREHOUSE_READ',
  );
  protected readonly canReadCashRegisters = this.sessionStore.hasPermission(
    'ORGANIZATION_CASH_REGISTER_READ',
  );

  constructor() {
    forkJoin({
      branches: this.canReadBranches ? this.organizationApi.listBranches('ACTIVE') : of([]),
      warehouses: this.canReadWarehouses
        ? this.organizationApi.listWarehouses(undefined, 'ACTIVE')
        : of([]),
      cashRegisters: this.canReadCashRegisters
        ? this.organizationApi.listCashRegisters(undefined, 'ACTIVE')
        : of([]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ branches, warehouses, cashRegisters }) =>
          this.contextStore.revalidate(branches, warehouses, cashRegisters),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible validar el contexto operativo.'),
          ),
      });
  }

  protected branchChanged(branchId: string | null): void {
    this.contextStore.selectBranch(branchId);
  }

  protected warehouseChanged(warehouseId: string | null): void {
    this.contextStore.selectWarehouse(warehouseId);
  }

  protected cashRegisterChanged(cashRegisterId: string | null): void {
    this.contextStore.selectCashRegister(cashRegisterId);
  }
}
