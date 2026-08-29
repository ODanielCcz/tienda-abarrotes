import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { InventoryApi } from '../../data-access/inventory-api';
import {
  InventoryCount,
  InventoryLot,
  InventoryReservation,
  StockBalance,
} from '../../models/inventory.model';

@Component({
  selector: 'app-advanced-inventory',
  imports: [
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './advanced-inventory.html',
  styleUrl: './advanced-inventory.scss',
})
export class AdvancedInventory {
  private readonly api = inject(InventoryApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly session = inject(AuthSessionStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly destinations = computed(() =>
    this.contextStore
      .warehouses()
      .filter((warehouse) => warehouse.warehouseId !== this.contextStore.context().warehouseId),
  );
  protected readonly stock = signal<StockBalance[]>([]);
  protected readonly lots = signal<InventoryLot[]>([]);
  protected readonly expiringLots = signal<InventoryLot[]>([]);
  protected readonly pendingCount = signal<InventoryCount | null>(null);
  protected readonly recentReservation = signal<InventoryReservation | null>(null);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly successMessage = signal<string | null>(null);
  protected reservationIdempotencyKey = crypto.randomUUID();

  protected readonly canAdjust = this.session.hasPermission('INVENTORY_ADJUSTMENT_CREATE');
  protected readonly canTransfer = this.session.hasPermission('INVENTORY_TRANSFER_CREATE');
  protected readonly canCreateCount = this.session.hasPermission('INVENTORY_COUNT_CREATE');
  protected readonly canConfirmCount = this.session.hasPermission('INVENTORY_COUNT_CONFIRM');
  protected readonly canReserve = this.session.hasPermission('INVENTORY_RESERVATION_CREATE');
  protected readonly canRelease = this.session.hasPermission('INVENTORY_RESERVATION_RELEASE');
  protected readonly canReadExpiring = this.session.hasPermission('INVENTORY_EXPIRING_LOT_READ');
  protected readonly lotColumns = ['lot', 'product', 'expiresAt', 'available'];

  protected readonly adjustmentForm = this.fb.group({
    productPresentationId: this.fb.nonNullable.control('', Validators.required),
    lotId: this.fb.control<string | null>(null),
    direction: this.fb.nonNullable.control<'IN' | 'OUT'>('IN'),
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    unitCost: this.fb.control<number | null>(null, Validators.min(0)),
    reason: this.fb.nonNullable.control('', Validators.required),
  });
  protected readonly transferForm = this.fb.group({
    toWarehouseId: this.fb.nonNullable.control('', Validators.required),
    productPresentationId: this.fb.nonNullable.control('', Validators.required),
    lotId: this.fb.control<string | null>(null),
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    unitCost: this.fb.control<number | null>(null, Validators.min(0)),
    reason: this.fb.nonNullable.control(''),
  });
  protected readonly countForm = this.fb.group({
    productPresentationId: this.fb.nonNullable.control('', Validators.required),
    lotId: this.fb.control<string | null>(null),
    countedQuantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
  });
  protected readonly reservationForm = this.fb.group({
    productPresentationId: this.fb.nonNullable.control('', Validators.required),
    lotId: this.fb.control<string | null>(null),
    quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
    sourceType: this.fb.nonNullable.control('MANUAL', Validators.required),
    sourceId: this.fb.nonNullable.control(crypto.randomUUID(), Validators.required),
    expiresAt: this.fb.nonNullable.control(this.tomorrowLocal(), Validators.required),
  });
  protected readonly expirationForm = this.fb.nonNullable.group({
    expiresBefore: this.addDaysLocal(30),
  });

  constructor() {
    this.load();
  }

  protected lotsFor(presentationId: string | null): InventoryLot[] {
    return presentationId
      ? this.lots().filter((lot) => lot.productPresentationId === presentationId)
      : [];
  }

  protected stockLabel(item: StockBalance): string {
    return `${item.productName} · ${item.presentationName} (${item.sku})`;
  }

  protected presentationLabel(productPresentationId: string): string {
    const item = this.stock().find(
      (candidate) => candidate.productPresentationId === productPresentationId,
    );
    return item ? this.stockLabel(item) : 'Presentación no disponible';
  }

  protected adjust(): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (!warehouseId || this.adjustmentForm.invalid) return;
    const value = this.adjustmentForm.getRawValue();
    this.run(
      this.api.adjust({
        warehouseId,
        reason: value.reason.trim(),
        items: [
          {
            productPresentationId: value.productPresentationId,
            lotId: value.lotId,
            direction: value.direction,
            quantity: Number(value.quantity),
            unitCost: value.unitCost,
          },
        ],
      }),
      'Ajuste registrado correctamente.',
    );
  }

  protected transfer(): void {
    const fromWarehouseId = this.contextStore.context().warehouseId;
    if (!fromWarehouseId || this.transferForm.invalid) return;
    const value = this.transferForm.getRawValue();
    this.run(
      this.api.transfer({
        fromWarehouseId,
        toWarehouseId: value.toWarehouseId,
        reason: value.reason.trim() || null,
        items: [
          {
            productPresentationId: value.productPresentationId,
            lotId: value.lotId,
            quantity: Number(value.quantity),
            unitCost: value.unitCost,
          },
        ],
      }),
      'Traspaso registrado correctamente.',
    );
  }

  protected createCount(): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (!warehouseId || this.countForm.invalid) return;
    const value = this.countForm.getRawValue();
    this.processing.set(true);
    this.clearMessages();
    this.api
      .createCount({
        warehouseId,
        items: [
          {
            productPresentationId: value.productPresentationId,
            lotId: value.lotId,
            countedQuantity: Number(value.countedQuantity),
          },
        ],
      })
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (count) => {
          this.pendingCount.set(count);
          this.successMessage.set(
            'Conteo abierto. Revísalo y confírmalo para ajustar diferencias.',
          );
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  protected confirmCount(): void {
    const count = this.pendingCount();
    if (!count) return;
    this.processing.set(true);
    this.clearMessages();
    this.api
      .confirmCount(count.inventoryCountId)
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.pendingCount.set(null);
          this.successMessage.set('Conteo confirmado y diferencias aplicadas.');
          this.load(false);
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  protected reserve(): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (!warehouseId || this.reservationForm.invalid) return;
    const value = this.reservationForm.getRawValue();
    this.processing.set(true);
    this.clearMessages();
    this.api
      .reserve({
        customerId: null,
        sourceType: value.sourceType.trim().toUpperCase(),
        sourceId: value.sourceId,
        idempotencyKey: this.reservationIdempotencyKey,
        expiresAt: new Date(value.expiresAt).toISOString(),
        items: [
          {
            warehouseId,
            productPresentationId: value.productPresentationId,
            lotId: value.lotId,
            quantity: Number(value.quantity),
          },
        ],
      })
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (reservation) => {
          this.recentReservation.set(reservation);
          this.reservationIdempotencyKey = crypto.randomUUID();
          this.successMessage.set('Reserva creada correctamente.');
          this.load(false);
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  protected releaseReservation(): void {
    const reservation = this.recentReservation();
    if (!reservation) return;
    this.processing.set(true);
    this.clearMessages();
    this.api
      .releaseReservation(reservation.reservationId)
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (released) => {
          this.recentReservation.set(released);
          this.successMessage.set('Reserva liberada correctamente.');
          this.load(false);
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  protected refreshExpiring(): void {
    this.loadExpiring(this.contextStore.context().warehouseId);
  }

  private run<T>(operation: import('rxjs').Observable<T>, message: string): void {
    this.processing.set(true);
    this.clearMessages();
    operation
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.successMessage.set(message);
          this.load(false);
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  private load(showSpinner = true): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (!warehouseId) return;
    if (showSpinner) this.loading.set(true);
    forkJoin({ stock: this.api.listStock(warehouseId), lots: this.api.listLots({ warehouseId }) })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ stock, lots }) => {
          this.stock.set(stock);
          this.lots.set(lots);
          if (this.canReadExpiring) this.loadExpiring(warehouseId);
        },
        error: (error: unknown) => this.showError(error),
      });
  }

  private loadExpiring(warehouseId?: string): void {
    this.api
      .listExpiringLots(this.expirationForm.controls.expiresBefore.value)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lots) =>
          this.expiringLots.set(
            warehouseId ? lots.filter((lot) => lot.warehouseId === warehouseId) : lots,
          ),
        error: (error: unknown) => this.showError(error),
      });
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }
  private showError(error: unknown): void {
    this.errorMessage.set(
      apiErrorMessage(error, 'No fue posible completar la operación de inventario.'),
    );
  }
  private addDaysLocal(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
  }
  private tomorrowLocal(): string {
    const date = new Date();
    date.setDate(date.getDate() + 1);
    return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }
}
