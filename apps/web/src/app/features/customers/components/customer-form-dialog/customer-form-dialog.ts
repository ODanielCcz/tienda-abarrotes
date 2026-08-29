import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { CustomerApi } from '../../data-access/customer-api';
import { Customer, CustomerRequest, CustomerType } from '../../models/customer.model';
@Component({
  selector: 'app-customer-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './customer-form-dialog.html',
  styleUrl: './customer-form-dialog.scss',
})
export class CustomerFormDialog {
  private readonly api = inject(CustomerApi);
  private readonly ref = inject(MatDialogRef<CustomerFormDialog, Customer | null>);
  protected readonly data = inject<{ customer: Customer | null }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    customerCode: [this.data.customer?.customerCode ?? '', Validators.maxLength(50)],
    customerType: this.fb.nonNullable.control<CustomerType>(
      this.data.customer?.customerType ?? 'PERSON',
    ),
    displayName: [
      this.data.customer?.displayName ?? '',
      [Validators.required, Validators.maxLength(200)],
    ],
    email: [this.data.customer?.email ?? '', [Validators.email, Validators.maxLength(254)]],
    phone: [this.data.customer?.phone ?? '', Validators.maxLength(40)],
  });
  protected submit(): void {
    const v = this.form.getRawValue();
    const request: CustomerRequest = {
      customerCode: this.optional(v.customerCode)?.toUpperCase() ?? null,
      customerType: v.customerType,
      displayName: v.displayName.trim(),
      email: this.optional(v.email)?.toLowerCase() ?? null,
      phone: this.optional(v.phone),
    };
    this.form.patchValue({
      customerCode: request.customerCode ?? '',
      displayName: request.displayName,
      email: request.email ?? '',
      phone: request.phone ?? '',
    });
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.errorMessage.set(null);
    const op = this.data.customer
      ? this.api.update(this.data.customer.customerId, request)
      : this.api.create(request);
    op.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (c) => this.ref.close(c),
      error: (e: unknown) =>
        this.errorMessage.set(apiErrorMessage(e, 'No fue posible guardar el cliente.')),
    });
  }
  private optional(value: string): string | null {
    const text = value.trim();
    return text || null;
  }
}
