import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { InventoryApi } from '../../data-access/inventory-api';
import {
  InventoryLot,
  InventoryPallet,
  StockBalance,
  StockMovement,
} from '../../models/inventory.model';

@Component({
  selector: 'app-inventory-overview',
  imports: [
    DatePipe,
    DecimalPipe,
    NgTemplateOutlet,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
    RouterLink,
  ],
  templateUrl: './inventory-overview.html',
  styleUrl: './inventory-overview.scss',
})
export class InventoryOverview {
  private readonly inventoryApi = inject(InventoryApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly destroyRef = inject(DestroyRef);
  private loadedWarehouseId: string | null = null;

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly stock = signal<StockBalance[]>([]);
  protected readonly lots = signal<InventoryLot[]>([]);
  protected readonly pallets = signal<InventoryPallet[]>([]);
  protected readonly movements = signal<StockMovement[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly canReadLots = this.sessionStore.hasPermission('INVENTORY_LOT_READ');
  protected readonly canReadPallets = this.sessionStore.hasPermission('INVENTORY_PALLET_READ');
  protected readonly canReadMovements = this.sessionStore.hasPermission('INVENTORY_MOVEMENT_READ');
  protected readonly canCreateReceipt = this.sessionStore.hasPermission('INVENTORY_RECEIPT_CREATE');

  protected readonly stockColumns = [
    'product',
    'sku',
    'onHand',
    'reserved',
    'allocated',
    'available',
    'averageCost',
    'updatedAt',
  ];
  protected readonly lotColumns = [
    'lotNumber',
    'presentation',
    'manufacturedAt',
    'expiresAt',
    'onHand',
    'available',
    'status',
  ];
  protected readonly palletColumns = ['code', 'externalCode', 'items', 'status', 'receivedAt'];
  protected readonly movementColumns = ['type', 'source', 'items', 'status', 'createdAt'];

  constructor() {
    effect(() => {
      const warehouseId = this.contextStore.context().warehouseId;
      if (warehouseId !== undefined && warehouseId !== this.loadedWarehouseId) {
        this.loadedWarehouseId = warehouseId;
        this.load(warehouseId);
      }
    });
  }

  protected refresh(): void {
    const warehouseId = this.contextStore.context().warehouseId;
    if (warehouseId !== undefined) this.load(warehouseId);
  }

  protected presentationName(productPresentationId: string): string {
    const balance = this.stock().find(
      (candidate) => candidate.productPresentationId === productPresentationId,
    );
    return balance === undefined
      ? 'Presentación no disponible'
      : `${balance.productName} · ${balance.presentationName}`;
  }

  protected palletItemCount(pallet: InventoryPallet): number {
    return pallet.items.reduce((total, item) => total + Number(item.quantity), 0);
  }

  private load(warehouseId: string): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      stock: this.inventoryApi.listStock(warehouseId),
      lots: this.canReadLots
        ? this.inventoryApi.listLots({ warehouseId })
        : of([] as InventoryLot[]),
      pallets: this.canReadPallets
        ? this.inventoryApi.listPallets(warehouseId)
        : of([] as InventoryPallet[]),
      movements: this.canReadMovements
        ? this.inventoryApi.listMovements({ warehouseId })
        : of([] as StockMovement[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ stock, lots, pallets, movements }) => {
          this.stock.set(stock);
          this.lots.set(lots);
          this.pallets.set(pallets);
          this.movements.set(movements);
        },
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar el inventario.')),
      });
  }
}
