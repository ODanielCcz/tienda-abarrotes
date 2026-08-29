import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { PurchasingApi } from '../../data-access/purchasing-api';
import { Supplier, SupplierUpsertRequest } from '../../models/purchasing.model';

@Component({
  selector: 'app-supplier-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './supplier-form-dialog.html',
  styleUrl: './supplier-form-dialog.scss',
})
export class SupplierFormDialog {
  private readonly api = inject(PurchasingApi);
  private readonly dialogRef = inject(MatDialogRef<SupplierFormDialog, Supplier | null>);
  protected readonly data = inject<{ supplier: Supplier | null }>(MAT_DIALOG_DATA);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group({
    supplierCode: [
      this.data.supplier?.supplierCode ?? '',
      [Validators.required, Validators.maxLength(50)],
    ],
    legalName: [
      this.data.supplier?.legalName ?? '',
      [Validators.required, Validators.maxLength(200)],
    ],
    tradeName: [this.data.supplier?.tradeName ?? '', [Validators.maxLength(200)]],
    taxId: [this.data.supplier?.taxId ?? '', [Validators.maxLength(20)]],
    email: [this.data.supplier?.email ?? '', [Validators.email, Validators.maxLength(254)]],
    phone: [this.data.supplier?.phone ?? '', [Validators.maxLength(40)]],
    creditDays: [this.data.supplier?.creditDays ?? 0, [Validators.required, Validators.min(0)]],
  });

  protected submit(): void {
    const raw = this.form.getRawValue();
    const request: SupplierUpsertRequest = {
      supplierCode: raw.supplierCode.trim().toUpperCase(),
      legalName: raw.legalName.trim(),
      tradeName: this.optionalText(raw.tradeName),
      taxId: this.optionalText(raw.taxId)?.toUpperCase() ?? null,
      email: this.optionalText(raw.email)?.toLowerCase() ?? null,
      phone: this.optionalText(raw.phone),
      creditDays: raw.creditDays,
    };
    this.form.patchValue({
      supplierCode: request.supplierCode,
      legalName: request.legalName,
      tradeName: request.tradeName ?? '',
      taxId: request.taxId ?? '',
      email: request.email ?? '',
      phone: request.phone ?? '',
    });
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const operation =
      this.data.supplier === null
        ? this.api.createSupplier(request)
        : this.api.updateSupplier(this.data.supplier.supplierId, request);
    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (supplier) => this.dialogRef.close(supplier),
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el proveedor.')),
    });
  }

  private optionalText(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }
}
