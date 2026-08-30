import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { Branch } from '../../../organization/models/organization.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { BillingApi } from '../../data-access/billing-api';
import { IssuerProfile, IssuerProfileRequest } from '../../models/billing.model';

@Component({
  selector: 'app-issuer-profile-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './issuer-profile-dialog.html',
  styleUrl: './issuer-profile-dialog.scss',
})
export class IssuerProfileDialog {
  private readonly api = inject(BillingApi);
  private readonly ref = inject(MatDialogRef<IssuerProfileDialog, IssuerProfile | null>);
  protected readonly data = inject<{ profile: IssuerProfile | null; branches: Branch[] }>(
    MAT_DIALOG_DATA,
  );
  private readonly fb = inject(FormBuilder);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    branchId: [this.data.profile?.branchId ?? '', Validators.required],
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
    defaultSeries: [this.data.profile?.defaultSeries ?? '', Validators.maxLength(25)],
  });
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    const request: IssuerProfileRequest = {
      branchId: value.branchId,
      rfc: value.rfc.trim().toUpperCase(),
      legalName: value.legalName.trim(),
      postalCode: value.postalCode.trim(),
      fiscalRegimeCode: value.fiscalRegimeCode.trim().toUpperCase(),
      defaultSeries: value.defaultSeries.trim().toUpperCase() || null,
    };
    this.submitting.set(true);
    this.errorMessage.set(null);
    const op = this.data.profile
      ? this.api.updateIssuerProfile(this.data.profile.issuerProfileId, request)
      : this.api.createIssuerProfile(request);
    op.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (profile) => this.ref.close(profile),
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el perfil emisor.')),
    });
  }
}
