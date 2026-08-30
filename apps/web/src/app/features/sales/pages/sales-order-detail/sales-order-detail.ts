import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { SalesApi } from '../../data-access/sales-api';
import { SalesOrder } from '../../models/sales.model';

@Component({
  selector: 'app-sales-order-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './sales-order-detail.html',
  styleUrl: './sales-order-detail.scss',
})
export class SalesOrderDetail {
  private readonly api = inject(SalesApi);
  private readonly route = inject(ActivatedRoute);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly salesOrderId = this.route.snapshot.paramMap.get('salesOrderId')!;

  protected readonly order = signal<SalesOrder | null>(null);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = ['product', 'lot', 'quantity', 'price', 'tax', 'total'];
  protected readonly canCancel = this.sessionStore.hasPermission('SALES_ORDER_CANCEL');

  constructor() {
    this.load();
  }

  protected cancel(): void {
    const order = this.order();
    if (!order || order.status !== 'CONFIRMED' || this.processing()) return;
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: {
          title: 'Cancelar venta',
          message: 'La cancelación repondrá el inventario. ¿Deseas continuar?',
          confirmLabel: 'Cancelar venta',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.processing.set(true);
          return this.api
            .cancel(this.salesOrderId)
            .pipe(finalize(() => this.processing.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (cancelled) => this.order.set(cancelled),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible cancelar la venta.')),
      });
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .get(this.salesOrderId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => this.order.set(order),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar la venta.')),
      });
  }
}
