import { DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { CatalogReferenceApi } from '../../../catalog/products/data-access/catalog-reference-api';
import { CustomerApi } from '../../../customers/data-access/customer-api';
import { Customer } from '../../../customers/models/customer.model';
import { InventoryApi } from '../../../inventory/data-access/inventory-api';
import { SalesApi } from '../../data-access/sales-api';
import { PosCartItem, PosCatalogItem } from '../../models/sales.model';

@Component({
  selector: 'app-point-of-sale',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './point-of-sale.html',
  styleUrl: './point-of-sale.scss',
})
export class PointOfSale {
  private readonly salesApi = inject(SalesApi);
  private readonly inventoryApi = inject(InventoryApi);
  private readonly catalogApi = inject(CatalogReferenceApi);
  private readonly customerApi = inject(CustomerApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly catalog = signal<PosCatalogItem[]>([]);
  protected readonly cart = signal<PosCartItem[]>([]);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly search = signal('');
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly customerId = new FormControl<string | null>(null);
  protected readonly canReadCustomers = this.sessionStore.hasPermission('SALES_CUSTOMER_READ');
  protected readonly filteredCatalog = computed(() => {
    const term = this.search().trim().toLocaleLowerCase();
    if (!term) return this.catalog();
    return this.catalog().filter((item) =>
      `${item.sku} ${item.productName} ${item.presentationName}`.toLocaleLowerCase().includes(term),
    );
  });
  protected readonly estimatedTotal = computed(() =>
    this.cart().reduce((total, item) => total + item.quantity * item.unitPrice, 0),
  );
  protected idempotencyKey = crypto.randomUUID();

  constructor() {
    this.load();
  }

  protected addToCart(item: PosCatalogItem): void {
    const current = this.cart();
    const existing = current.find(
      (candidate) => candidate.productPresentationId === item.productPresentationId,
    );
    if (existing) {
      this.changeQuantity(item.productPresentationId, existing.quantity + 1);
      return;
    }
    this.cart.set([...current, { ...item, quantity: 1 }]);
  }

  protected changeQuantity(productPresentationId: string, quantity: number): void {
    this.cart.update((items) =>
      items.map((item) => {
        if (item.productPresentationId !== productPresentationId) return item;
        const normalized = Number.isFinite(quantity) ? quantity : 1;
        return {
          ...item,
          quantity: Math.min(item.availableQuantity, Math.max(1, normalized)),
        };
      }),
    );
  }

  protected removeFromCart(productPresentationId: string): void {
    this.cart.update((items) =>
      items.filter((item) => item.productPresentationId !== productPresentationId),
    );
  }

  protected submit(): void {
    const context = this.contextStore.context();
    const items = this.cart();
    if (!context.warehouseId || items.length === 0 || this.processing()) return;
    const currencies = new Set(items.map((item) => item.currencyCode));
    if (currencies.size !== 1) {
      this.errorMessage.set('La venta no puede mezclar precios de monedas diferentes.');
      return;
    }

    this.processing.set(true);
    this.errorMessage.set(null);
    this.salesApi
      .create({
        warehouseId: context.warehouseId,
        customerId: this.customerId.value,
        deviceId: null,
        channel: 'POS',
        currencyCode: items[0].currencyCode,
        idempotencyKey: this.idempotencyKey,
        items: items.map((item) => ({
          productPresentationId: item.productPresentationId,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          discountAmount: 0,
        })),
      })
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => void this.router.navigate(['/sales', order.salesOrderId]),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible registrar la venta.')),
      });
  }

  private load(): void {
    const context = this.contextStore.context();
    if (!context.branchId || !context.warehouseId) {
      this.loading.set(false);
      this.errorMessage.set('Selecciona una sucursal y un almacén antes de vender.');
      return;
    }

    this.loading.set(true);
    forkJoin({
      stock: this.inventoryApi.listStock(context.warehouseId),
      prices: this.catalogApi.listPrices(context.branchId),
      customers: this.canReadCustomers
        ? this.customerApi.list({ status: 'ACTIVE' })
        : of([] as Customer[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ stock, prices, customers }) => {
          const pricesByPresentation = new Map(
            prices.map((price) => [price.productPresentationId, price]),
          );
          this.catalog.set(
            stock
              .filter((balance) => balance.availableQuantity > 0)
              .flatMap((balance) => {
                const price = pricesByPresentation.get(balance.productPresentationId);
                return price
                  ? [
                      {
                        productPresentationId: balance.productPresentationId,
                        sku: balance.sku,
                        productName: balance.productName,
                        presentationName: balance.presentationName,
                        availableQuantity: balance.availableQuantity,
                        unitPrice: price.amount,
                        currencyCode: price.currencyCode,
                      },
                    ]
                  : [];
              }),
          );
          this.customers.set(customers);
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible preparar el punto de venta.'),
          ),
      });
  }
}
