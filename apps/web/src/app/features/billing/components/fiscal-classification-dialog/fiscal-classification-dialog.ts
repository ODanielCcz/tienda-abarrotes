import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { CatalogUnit } from '../../../catalog/products/models/catalog-reference.model';
import { Product } from '../../../catalog/products/models/product.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { BillingApi } from '../../data-access/billing-api';

@Component({
  selector: 'app-fiscal-classification-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './fiscal-classification-dialog.html',
  styleUrl: './fiscal-classification-dialog.scss',
})
export class FiscalClassificationDialog {
  private readonly api = inject(BillingApi);
  private readonly ref = inject(MatDialogRef<FiscalClassificationDialog, boolean>);
  protected readonly data = inject<{
    mode: 'PRODUCT' | 'UNIT';
    products: Product[];
    units: CatalogUnit[];
  }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    targetId: ['', Validators.required],
    code: ['', Validators.required],
  });
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    const code = value.code.trim().toUpperCase();
    if (
      (this.data.mode === 'PRODUCT' && !/^\d{8}$/.test(code)) ||
      (this.data.mode === 'UNIT' && (code.length < 1 || code.length > 5))
    ) {
      this.errorMessage.set(
        this.data.mode === 'PRODUCT'
          ? 'El código SAT de producto debe tener 8 dígitos.'
          : 'El código SAT de unidad admite hasta 5 caracteres.',
      );
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const op =
      this.data.mode === 'PRODUCT'
        ? this.api.classifyProduct(value.targetId, code)
        : this.api.classifyUnit(value.targetId, code);
    op.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: () => this.ref.close(true),
      error: (error: unknown) =>
        this.errorMessage.set(
          apiErrorMessage(error, 'No fue posible guardar la clasificación fiscal.'),
        ),
    });
  }
}
