import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { CatalogReferenceApi } from '../../data-access/catalog-reference-api';
import { ProductApi } from '../../data-access/product-api';
import { CatalogTax, CatalogUnit } from '../../models/catalog-reference.model';
import { ProductPresentationUpsertRequest } from '../../models/product-presentation-request.model';
import { ProductPresentation } from '../../models/product.model';

export interface PresentationFormDialogData {
  productId: string;
  presentation: ProductPresentation | null;
}

@Component({
  selector: 'app-presentation-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './presentation-form-dialog.html',
  styleUrl: './presentation-form-dialog.scss',
})
export class PresentationFormDialog {
  private readonly productApi = inject(ProductApi);
  private readonly referenceApi = inject(CatalogReferenceApi);
  private readonly dialogRef = inject<MatDialogRef<PresentationFormDialog, ProductPresentation>>(
    MatDialogRef,
  );
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  protected readonly data = inject<PresentationFormDialogData>(MAT_DIALOG_DATA);

  protected readonly units = signal<CatalogUnit[]>([]);
  protected readonly taxes = signal<CatalogTax[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.group({
    unitId: this.formBuilder.nonNullable.control('', Validators.required),
    taxId: this.formBuilder.control<string | null>(null),
    sku: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(80),
      Validators.pattern(/^[A-Za-z0-9_-]+$/),
    ]),
    name: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(200),
    ]),
    conversionFactor: this.formBuilder.nonNullable.control(1, Validators.min(0.000001)),
    netContent: this.formBuilder.control<number | null>(null, Validators.min(0.000001)),
    minimumStock: this.formBuilder.nonNullable.control(0, Validators.min(0)),
  });

  constructor() {
    const presentation = this.data.presentation;
    if (presentation !== null) {
      this.form.setValue({
        unitId: presentation.unitId,
        taxId: presentation.taxId,
        sku: presentation.sku,
        name: presentation.name,
        conversionFactor: presentation.conversionFactor,
        netContent: presentation.netContent,
        minimumStock: presentation.minimumStock,
      });
    }

    forkJoin({
      units: this.referenceApi.listUnits(),
      taxes: this.referenceApi.listTaxes(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ units, taxes }) => {
          this.units.set(units);
          this.taxes.set(taxes);
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible cargar unidades e impuestos.'),
          ),
      });
  }

  protected submit(): void {
    const raw = this.form.getRawValue();
    const payload: ProductPresentationUpsertRequest = {
      unitId: raw.unitId,
      taxId: raw.taxId,
      sku: raw.sku.trim().toUpperCase(),
      name: raw.name.trim(),
      conversionFactor: raw.conversionFactor,
      netContent: raw.netContent,
      minimumStock: raw.minimumStock,
    };
    this.form.patchValue({ sku: payload.sku, name: payload.name });
    this.form.markAllAsTouched();

    if (this.form.invalid || this.submitting() || this.loading()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const request$ = this.data.presentation === null
      ? this.productApi.createPresentation(this.data.productId, payload)
      : this.productApi.updatePresentation(this.data.presentation.id, payload);

    request$
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (presentation) => this.dialogRef.close(presentation),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar la presentación.')),
      });
  }
}
