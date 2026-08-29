import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { ProductApi } from '../../../catalog/products/data-access/product-api';
import { Product, ProductPresentation } from '../../../catalog/products/models/product.model';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { CreatePurchaseRequest, Supplier } from '../../models/purchasing.model';

@Component({
  selector: 'app-purchase-form',
  imports: [
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
  templateUrl: './purchase-form.html',
  styleUrl: './purchase-form.scss',
})
export class PurchaseForm {
  private readonly api = inject(PurchasingApi);
  private readonly productApi = inject(ProductApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly presentationCache = new Map<string, ProductPresentation[]>();
  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly suppliers = signal<Supplier[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly idempotencyKey = crypto.randomUUID();
  protected readonly form = this.fb.group({
    supplierId: this.fb.nonNullable.control('', Validators.required),
    supplierDocument: this.fb.nonNullable.control('', Validators.maxLength(100)),
    currencyCode: this.fb.nonNullable.control('MXN', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(3),
    ]),
    items: this.fb.array<FormGroup>([this.createItem()]),
  });
  constructor() {
    forkJoin({
      suppliers: this.api.listSuppliers({ status: 'ACTIVE' }),
      products: this.productApi.list({
        page: 0,
        size: 100,
        status: 'ACTIVE',
        sortBy: 'NAME',
        direction: 'ASC',
      }),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ suppliers, products }) => {
          this.suppliers.set(suppliers);
          this.products.set(products.content.filter((p) => p.productType === 'GOODS'));
        },
        error: (e: unknown) =>
          this.errorMessage.set(apiErrorMessage(e, 'No fue posible preparar la compra.')),
      });
  }
  protected get items(): FormArray<FormGroup> {
    return this.form.controls.items;
  }
  protected addItem(): void {
    this.items.push(this.createItem());
  }
  protected removeItem(i: number): void {
    this.items.removeAt(i);
  }
  protected loadPresentations(productId: string, item?: FormGroup): void {
    item?.get('productPresentationId')?.setValue('');
    if (!productId || this.presentationCache.has(productId)) return;
    this.productApi
      .listPresentations(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (p) =>
          this.presentationCache.set(
            productId,
            p.filter((x) => x.status === 'ACTIVE'),
          ),
        error: (e: unknown) =>
          this.errorMessage.set(apiErrorMessage(e, 'No fue posible consultar las presentaciones.')),
      });
  }
  protected presentationsFor(productId: string | null): ProductPresentation[] {
    return productId ? (this.presentationCache.get(productId) ?? []) : [];
  }
  protected submit(): void {
    this.form.markAllAsTouched();
    const warehouseId = this.contextStore.context().warehouseId;
    if (this.form.invalid || !warehouseId || this.submitting()) return;
    const raw = this.form.getRawValue();
    const request: CreatePurchaseRequest = {
      warehouseId,
      supplierId: raw.supplierId,
      supplierDocument: raw.supplierDocument.trim() || null,
      currencyCode: raw.currencyCode.trim().toUpperCase(),
      idempotencyKey: this.idempotencyKey,
      items: raw.items.map((item) => ({
        productPresentationId: String(item['productPresentationId']),
        quantity: Number(item['quantity']),
        unitCost: Number(item['unitCost']),
        discountAmount: Number(item['discountAmount'] ?? 0),
        taxAmount: Number(item['taxAmount'] ?? 0),
      })),
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api
      .createPurchase(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (p) => void this.router.navigate(['/purchasing/purchases', p.purchaseId]),
        error: (e: unknown) =>
          this.errorMessage.set(apiErrorMessage(e, 'No fue posible crear la compra.')),
      });
  }
  private createItem(): FormGroup {
    return this.fb.group({
      productId: this.fb.nonNullable.control('', Validators.required),
      productPresentationId: this.fb.nonNullable.control('', Validators.required),
      quantity: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
      unitCost: this.fb.control<number | null>(null, [Validators.required, Validators.min(0)]),
      discountAmount: this.fb.nonNullable.control(0, Validators.min(0)),
      taxAmount: this.fb.nonNullable.control(0, Validators.min(0)),
    });
  }
}
