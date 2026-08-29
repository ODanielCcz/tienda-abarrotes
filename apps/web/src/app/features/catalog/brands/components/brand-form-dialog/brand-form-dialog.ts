import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { BrandApi } from '../../data-access/brand-api';
import { Brand } from '../../models/brand.model';

export interface BrandFormDialogData {
  brand: Brand | null;
}

@Component({
  selector: 'app-brand-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './brand-form-dialog.html',
  styleUrl: './brand-form-dialog.scss',
})
export class BrandFormDialog {
  private readonly brandApi = inject(BrandApi);
  private readonly dialogRef = inject(MatDialogRef<BrandFormDialog, Brand | null>);
  protected readonly data = inject<BrandFormDialogData>(MAT_DIALOG_DATA);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.nonNullable.group({
    code: [
      this.data.brand?.code ?? '',
      [Validators.required, Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9_-]+$/)],
    ],
    name: [
      this.data.brand?.name ?? '',
      [Validators.required, Validators.maxLength(150)],
    ],
  });

  protected submit(): void {
    const payload = {
      code: this.form.controls.code.value.trim().toUpperCase(),
      name: this.form.controls.name.value.trim(),
    };
    this.form.setValue(payload, { emitEvent: false });
    this.form.markAllAsTouched();

    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const request$ = this.data.brand
      ? this.brandApi.update(this.data.brand.id, payload)
      : this.brandApi.create(payload);

    request$
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (brand) => this.dialogRef.close(brand),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar la marca.')),
      });
  }
}
