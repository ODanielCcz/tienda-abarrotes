import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { Device } from '../../../organization/models/organization.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { SyncApi } from '../../data-access/sync-api';
import {
  DeviceCheckpoint,
  SyncOperation,
  SyncOperationRequest,
  SyncOperationType,
} from '../../models/sync.model';

@Component({
  selector: 'app-sync-operation-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './sync-operation-dialog.html',
  styleUrl: './sync-operation-dialog.scss',
})
export class SyncOperationDialog {
  private readonly api = inject(SyncApi);
  private readonly ref = inject(MatDialogRef<SyncOperationDialog, SyncOperation | null>);
  protected readonly data = inject<{ device: Device; checkpoint: DeviceCheckpoint }>(
    MAT_DIALOG_DATA,
  );
  private readonly fb = inject(FormBuilder);
  protected operationId = crypto.randomUUID();
  protected idempotencyKey = crypto.randomUUID();
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    operationType: this.fb.nonNullable.control<SyncOperationType>('CART_UPSERT'),
    aggregateId: [''],
    payload: [this.cartTemplate(), Validators.required],
  });
  protected operationChanged(type: SyncOperationType): void {
    this.form.controls.payload.setValue(
      type === 'CART_UPSERT' ? this.cartTemplate() : this.countTemplate(),
    );
    this.form.controls.aggregateId.setValue('');
  }
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    let payload: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(value.payload);
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error();
      payload = parsed as Record<string, unknown>;
    } catch {
      this.errorMessage.set('El payload debe ser un objeto JSON válido.');
      return;
    }
    const request: SyncOperationRequest = {
      operationId: this.operationId,
      deviceId: this.data.device.deviceId,
      deviceSequence: this.data.checkpoint.lastReceivedSequence + 1,
      idempotencyKey: this.idempotencyKey,
      operationType: value.operationType,
      aggregateType: value.operationType === 'CART_UPSERT' ? 'CART' : 'INVENTORY_COUNT',
      aggregateId: value.aggregateId.trim() || null,
      payload,
      clientCreatedAt: new Date().toISOString(),
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api
      .ingest(request)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (operation) => this.ref.close(operation),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible procesar la operación Sync.'),
          ),
      });
  }
  private cartTemplate(): string {
    return JSON.stringify(
      {
        branchId: this.data.device.branchId,
        currencyCode: 'MXN',
        expiresAt: null,
        items: [
          {
            productPresentationId: '00000000-0000-0000-0000-000000000000',
            quantity: 1,
            unitPriceSnapshot: 0,
          },
        ],
      },
      null,
      2,
    );
  }
  private countTemplate(): string {
    return JSON.stringify(
      {
        warehouseId: this.data.device.warehouseId,
        items: [
          {
            productPresentationId: '00000000-0000-0000-0000-000000000000',
            lotId: null,
            countedQuantity: 0,
          },
        ],
      },
      null,
      2,
    );
  }
}
