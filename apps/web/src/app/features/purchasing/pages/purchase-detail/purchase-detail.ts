import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { filter, finalize, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { Purchase, PurchaseItem, ReceivePurchaseRequest } from '../../models/purchasing.model';

@Component({
  selector: 'app-purchase-detail',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './purchase-detail.html',
  styleUrl: './purchase-detail.scss',
})
export class PurchaseDetail {
  private readonly api = inject(PurchasingApi);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly purchaseId = this.route.snapshot.paramMap.get('purchaseId')!;
  protected readonly purchase = signal<Purchase | null>(null);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly itemColumns = ['product', 'quantity', 'received', 'cost', 'total'];
  protected receiptIdempotencyKey = crypto.randomUUID();
  protected readonly receiptForm = this.fb.group({ items: this.fb.array<FormGroup>([]) });
  protected readonly canConfirm = this.session.hasPermission('PURCHASING_PURCHASE_CONFIRM');
  protected readonly canReceive = this.session.hasPermission('PURCHASING_PURCHASE_RECEIVE');
  constructor() {
    this.load();
  }
  protected get receiptItems(): FormArray<FormGroup> {
    return this.receiptForm.controls.items;
  }
  protected confirm(): void {
    const purchase = this.purchase();
    if (!purchase || purchase.status !== 'DRAFT' || this.processing()) return;
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: {
          title: 'Confirmar compra',
          message: 'Al confirmar se congelan costos, impuestos y totales. ¿Deseas continuar?',
          confirmLabel: 'Confirmar',
        },
      })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => {
          this.processing.set(true);
          return this.api
            .confirmPurchase(this.purchaseId)
            .pipe(finalize(() => this.processing.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (p) => {
          this.purchase.set(p);
          this.buildReceiptItems(p.items);
        },
        error: (e: unknown) => this.showError(e, 'No fue posible confirmar la compra.'),
      });
  }
  protected receive(): void {
    const purchase = this.purchase();
    if (
      !purchase ||
      !['CONFIRMED', 'PARTIALLY_RECEIVED'].includes(purchase.status) ||
      this.receiptForm.invalid ||
      this.processing()
    )
      return;
    const request: ReceivePurchaseRequest = {
      idempotencyKey: this.receiptIdempotencyKey,
      items: this.receiptItems
        .getRawValue()
        .filter((i) => Number(i['quantity']) > 0)
        .map((i) => ({
          purchaseItemId: String(i['purchaseItemId']),
          lotNumber: this.optional(String(i['lotNumber'] ?? '')),
          manufacturedAt: this.optional(String(i['manufacturedAt'] ?? '')),
          expiresAt: this.optional(String(i['expiresAt'] ?? '')),
          quantity: Number(i['quantity']),
        })),
      pallets: [],
    };
    if (request.items.length === 0) {
      this.errorMessage.set('Captura una cantidad para recibir.');
      return;
    }
    this.processing.set(true);
    this.errorMessage.set(null);
    this.api
      .receivePurchase(this.purchaseId, request)
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.receiptIdempotencyKey = crypto.randomUUID();
          this.load(false);
        },
        error: (e: unknown) => this.showError(e, 'No fue posible recibir la compra.'),
      });
  }
  protected remaining(item: PurchaseItem): number {
    return Number(item.quantity) - Number(item.receivedQuantity);
  }
  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .getPurchase(this.purchaseId)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (p) => {
          this.purchase.set(p);
          this.buildReceiptItems(p.items);
        },
        error: (e: unknown) => this.showError(e, 'No fue posible consultar la compra.'),
      });
  }
  private buildReceiptItems(items: PurchaseItem[]): void {
    this.receiptItems.clear();
    for (const item of items.filter((i) => this.remaining(i) > 0)) {
      this.receiptItems.push(
        this.fb.group({
          purchaseItemId: this.fb.nonNullable.control(item.purchaseItemId),
          productName: this.fb.nonNullable.control(item.productNameSnapshot),
          sku: this.fb.nonNullable.control(item.skuSnapshot),
          lotNumber: this.fb.nonNullable.control(''),
          manufacturedAt: this.fb.nonNullable.control(''),
          expiresAt: this.fb.nonNullable.control(''),
          quantity: this.fb.nonNullable.control(this.remaining(item), [
            Validators.required,
            Validators.min(0.001),
            Validators.max(this.remaining(item)),
          ]),
        }),
      );
    }
  }
  private optional(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }
  private showError(e: unknown, fallback: string): void {
    this.errorMessage.set(apiErrorMessage(e, fallback));
  }
}
