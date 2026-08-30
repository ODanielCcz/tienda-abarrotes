import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { SalesApi } from '../../data-access/sales-api';
import { SalesOrder } from '../../models/sales.model';

@Component({
  selector: 'app-sales-overview',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './sales-overview.html',
  styleUrl: './sales-overview.scss',
})
export class SalesOverview {
  private readonly api = inject(SalesApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly orders = signal<SalesOrder[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = ['number', 'createdAt', 'status', 'payment', 'total', 'actions'];
  protected readonly canCreate = this.sessionStore.hasPermission('SALES_ORDER_CREATE');

  constructor() {
    this.load();
  }

  protected refresh(): void {
    this.load();
  }

  private load(): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (!warehouseId) {
      this.loading.set(false);
      this.errorMessage.set('Selecciona un almacén para consultar ventas.');
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .list({ warehouseId })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (orders) => this.orders.set(orders),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar las ventas.')),
      });
  }
}
