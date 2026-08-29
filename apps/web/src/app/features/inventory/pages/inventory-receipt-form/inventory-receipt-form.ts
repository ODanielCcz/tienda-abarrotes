import { NgTemplateOutlet } from '@angular/common';
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
import { finalize } from 'rxjs';

import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { ProductApi } from '../../../catalog/products/data-access/product-api';
import { Product, ProductPresentation } from '../../../catalog/products/models/product.model';
import { InventoryApi } from '../../data-access/inventory-api';
import {
  CreateInventoryReceiptRequest,
  InventoryReceiptItemRequest,
} from '../../models/inventory.model';

type ReceiptItemForm = FormGroup;
type PalletForm = FormGroup;

@Component({
  selector: 'app-inventory-receipt-form',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './inventory-receipt-form.html',
  styleUrl: './inventory-receipt-form.scss',
})
export class InventoryReceiptForm {
  private readonly inventoryApi = inject(InventoryApi);
  private readonly productApi = inject(ProductApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly presentationCache = new Map<string, ProductPresentation[]>();

  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly policyMessage = signal<string | null>(null);
  protected readonly idempotencyKey = crypto.randomUUID();
  protected readonly form = this.formBuilder.group({
    reason: this.formBuilder.nonNullable.control('', [Validators.maxLength(500)]),
    items: this.formBuilder.array<ReceiptItemForm>([this.createItemForm()]),
    pallets: this.formBuilder.array<PalletForm>([]),
  });

  constructor() {
    this.productApi
      .list({ page: 0, size: 100, status: 'ACTIVE', sortBy: 'NAME', direction: 'ASC' })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (page) =>
          this.products.set(
            page.content.filter(
              (product) => product.productType === 'GOODS' && product.tracksInventory,
            ),
          ),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible consultar los productos recibibles.'),
          ),
      });
  }

  protected get items(): FormArray<ReceiptItemForm> {
    return this.form.controls.items;
  }

  protected get pallets(): FormArray<PalletForm> {
    return this.form.controls.pallets;
  }

  protected palletItems(index: number): FormArray<ReceiptItemForm> {
    return this.pallets.at(index).get('items') as FormArray<ReceiptItemForm>;
  }

  protected addItem(target: FormArray<ReceiptItemForm> = this.items): void {
    target.push(this.createItemForm());
  }

  protected removeItem(index: number, target: FormArray<ReceiptItemForm> = this.items): void {
    target.removeAt(index);
  }

  protected addPallet(): void {
    this.pallets.push(
      this.formBuilder.group({
        externalPalletCode: this.formBuilder.nonNullable.control('', [Validators.maxLength(100)]),
        items: this.formBuilder.array<ReceiptItemForm>([this.createItemForm()]),
      }),
    );
  }

  protected removePallet(index: number): void {
    this.pallets.removeAt(index);
  }

  protected loadPresentations(productId: string, item?: FormGroup): void {
    item?.get('productPresentationId')?.setValue('');
    if (!productId || this.presentationCache.has(productId)) return;
    this.productApi
      .listPresentations(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (presentations) =>
          this.presentationCache.set(
            productId,
            presentations.filter((presentation) => presentation.status === 'ACTIVE'),
          ),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible consultar las presentaciones del producto.'),
          ),
      });
  }

  protected presentationsFor(productId: string | null): ProductPresentation[] {
    return productId === null ? [] : (this.presentationCache.get(productId) ?? []);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    this.policyMessage.set(this.validateReceiptPolicies());
    const warehouseId = this.contextStore.context().warehouseId;
    if (this.form.invalid || this.policyMessage() !== null || warehouseId === undefined || this.submitting()) {
      return;
    }

    const raw = this.form.getRawValue();
    const request: CreateInventoryReceiptRequest = {
      warehouseId,
      supplierId: null,
      idempotencyKey: this.idempotencyKey,
      reason: this.optionalText(raw.reason),
      items: raw.items.map((item) => this.toRequestItem(item)),
      pallets: raw.pallets.map((pallet) => ({
        externalPalletCode: this.optionalText(String(pallet['externalPalletCode'] ?? '')),
        items: (pallet['items'] as Record<string, unknown>[]).map((item) =>
          this.toRequestItem(item),
        ),
      })),
    };

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.inventoryApi
      .createReceipt(request)
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => void this.router.navigate(['/inventory']),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible registrar la recepción.'),
          ),
      });
  }

  private createItemForm(): ReceiptItemForm {
    return this.formBuilder.group({
      productId: this.formBuilder.nonNullable.control('', Validators.required),
      productPresentationId: this.formBuilder.nonNullable.control('', Validators.required),
      lotNumber: this.formBuilder.nonNullable.control('', [Validators.maxLength(100)]),
      manufacturedAt: this.formBuilder.nonNullable.control(''),
      expiresAt: this.formBuilder.nonNullable.control(''),
      quantity: this.formBuilder.control<number | null>(null, [Validators.required, Validators.min(0.001)]),
      unitCost: this.formBuilder.control<number | null>(null, [Validators.min(0)]),
    });
  }

  private validateReceiptPolicies(): string | null {
    const groups = [
      ...this.items.controls,
      ...this.pallets.controls.flatMap((_, index) => this.palletItems(index).controls),
    ];
    if (groups.length === 0) return 'Agrega al menos una partida o un pallet.';
    for (const group of groups) {
      const value = group.getRawValue();
      const product = this.products().find((candidate) => candidate.id === value.productId);
      if (product?.tracksLots && !value.lotNumber.trim()) {
        return `${product.name} requiere número de lote.`;
      }
      if (product?.tracksExpiration && !value.expiresAt) {
        return `${product.name} requiere fecha de caducidad.`;
      }
      if (product !== undefined && !product.tracksLots && (value.lotNumber || value.manufacturedAt || value.expiresAt)) {
        return `${product.name} no controla lotes ni fechas de lote.`;
      }
    }
    return null;
  }

  private toRequestItem(value: Record<string, unknown>): InventoryReceiptItemRequest {
    return {
      productPresentationId: String(value['productPresentationId']),
      lotNumber: this.optionalText(String(value['lotNumber'] ?? '')),
      manufacturedAt: this.optionalText(String(value['manufacturedAt'] ?? '')),
      expiresAt: this.optionalText(String(value['expiresAt'] ?? '')),
      quantity: Number(value['quantity']),
      unitCost: value['unitCost'] === null ? null : Number(value['unitCost']),
    };
  }

  private optionalText(value: string): string | null {
    const trimmed = value.trim();
    return trimmed ? trimmed : null;
  }
}
