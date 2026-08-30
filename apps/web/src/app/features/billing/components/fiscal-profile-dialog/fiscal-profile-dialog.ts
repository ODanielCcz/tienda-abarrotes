import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { Customer } from '../../../customers/models/customer.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { BillingApi } from '../../data-access/billing-api';
import { FiscalProfile, FiscalProfileRequest } from '../../models/billing.model';

@Component({
  selector: 'app-fiscal-profile-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './fiscal-profile-dialog.html',
  styleUrl: './fiscal-profile-dialog.scss',
})
export class FiscalProfileDialog {
  private readonly api = inject(BillingApi);
  private readonly ref = inject(MatDialogRef<FiscalProfileDialog, FiscalProfile | null>);
  protected readonly data = inject<{ profile: FiscalProfile | null; customers: Customer[] }>(
    MAT_DIALOG_DATA,
  );
  private readonly fb = inject(FormBuilder);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    customerId: [this.data.profile?.customerId ?? '', Validators.required],
    rfc: [this.data.profile?.rfc ?? '', [Validators.required, Validators.maxLength(13)]],
    legalName: [
      this.data.profile?.legalName ?? '',
      [Validators.required, Validators.maxLength(300)],
    ],
    postalCode: [
      this.data.profile?.postalCode ?? '',
      [Validators.required, Validators.pattern(/^\d{5}$/)],
    ],
    fiscalRegimeCode: [
      this.data.profile?.fiscalRegimeCode ?? '',
      [Validators.required, Validators.maxLength(5)],
    ],
    cfdiUseCode: [this.data.profile?.cfdiUseCode ?? '', Validators.maxLength(5)],
    email: [this.data.profile?.email ?? '', [Validators.email, Validators.maxLength(254)]],
  });
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    const request: FiscalProfileRequest = {
      customerId: value.customerId,
      rfc: value.rfc.trim().toUpperCase(),
      legalName: value.legalName.trim(),
      postalCode: value.postalCode.trim(),
      fiscalRegimeCode: value.fiscalRegimeCode.trim().toUpperCase(),
      cfdiUseCode: value.cfdiUseCode.trim().toUpperCase() || null,
      email: value.email.trim().toLowerCase() || null,
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    const op = this.data.profile
      ? this.api.updateFiscalProfile(this.data.profile.fiscalProfileId, request)
      : this.api.createFiscalProfile(request);
    op.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (profile) => this.ref.close(profile),
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el perfil fiscal.')),
    });
  }
}
