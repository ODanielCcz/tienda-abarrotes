import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { filter, finalize, forkJoin, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { SupplierFormDialog } from '../../components/supplier-form-dialog/supplier-form-dialog';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { Purchase, Supplier, SupplierStatus } from '../../models/purchasing.model';

@Component({
  selector: 'app-purchasing-overview',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './purchasing-overview.html',
  styleUrl: './purchasing-overview.scss',
})
export class PurchasingOverview {
  private readonly api = inject(PurchasingApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly purchases = signal<Purchase[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly supplierColumns = [
    'code',
    'name',
    'contact',
    'creditDays',
    'status',
    'actions',
  ];
  protected readonly purchaseColumns = [
    'document',
    'supplier',
    'status',
    'total',
    'createdAt',
    'actions',
  ];
  protected readonly canCreateSupplier = this.sessionStore.hasPermission(
    'PURCHASING_SUPPLIER_CREATE',
  );
  protected readonly canReadSuppliers = this.sessionStore.hasPermission('PURCHASING_SUPPLIER_READ');
  protected readonly canReadPurchases = this.sessionStore.hasPermission('PURCHASING_PURCHASE_READ');
  protected readonly canUpdateSupplier = this.sessionStore.hasPermission(
    'PURCHASING_SUPPLIER_UPDATE',
  );
  protected readonly canChangeSupplierStatus = this.sessionStore.hasPermission(
    'PURCHASING_SUPPLIER_STATUS',
  );
  protected readonly canCreatePurchase = this.sessionStore.hasPermission(
    'PURCHASING_PURCHASE_CREATE',
  );

  constructor() {
    this.load();
  }

  protected refresh(): void {
    this.load();
  }

  protected supplierName(id: string): string {
    const supplier = this.suppliers().find((candidate) => candidate.supplierId === id);
    return supplier?.tradeName || supplier?.legalName || 'Proveedor no disponible';
  }

  protected openSupplierForm(supplier: Supplier | null): void {
    this.dialog
      .open(SupplierFormDialog, {
        width: '48rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { supplier },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  protected changeSupplierStatus(supplier: Supplier): void {
    const status: SupplierStatus = supplier.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: {
          title: status === 'ACTIVE' ? 'Activar proveedor' : 'Desactivar proveedor',
          message: `¿Deseas cambiar el estado de ${supplier.legalName}?`,
          confirmLabel: 'Confirmar',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => this.api.changeSupplierStatus(supplier.supplierId, status)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.load(),
        error: (error: unknown) =>
          this.showError(error, 'No fue posible cambiar el estado del proveedor.'),
      });
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    const warehouseId = this.contextStore.context().warehouseId;
    forkJoin({
      suppliers: this.canReadSuppliers ? this.api.listSuppliers({}) : of([] as Supplier[]),
      purchases: this.canReadPurchases
        ? this.api.listPurchases(warehouseId ? { warehouseId } : {})
        : of([] as Purchase[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ suppliers, purchases }) => {
          this.suppliers.set(suppliers);
          this.purchases.set(purchases);
        },
        error: (error: unknown) =>
          this.showError(error, 'No fue posible consultar compras y proveedores.'),
      });
  }

  private showError(error: unknown, fallback: string): void {
    this.errorMessage.set(apiErrorMessage(error, fallback));
  }
}
