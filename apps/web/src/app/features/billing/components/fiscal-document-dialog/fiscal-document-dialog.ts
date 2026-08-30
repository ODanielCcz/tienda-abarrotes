import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { finalize } from 'rxjs';

import { SalesOrder } from '../../../sales/models/sales.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { BillingApi } from '../../data-access/billing-api';
import { FiscalDocument, FiscalProfile, IssuerProfile } from '../../models/billing.model';

@Component({
  selector: 'app-fiscal-document-dialog',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './fiscal-document-dialog.html',
  styleUrl: './fiscal-document-dialog.scss',
})
export class FiscalDocumentDialog {
  private readonly api = inject(BillingApi);
  private readonly ref = inject(MatDialogRef<FiscalDocumentDialog, FiscalDocument | null>);
  protected readonly data = inject<{
    document: FiscalDocument | null;
    sales: SalesOrder[];
    issuers: IssuerProfile[];
    profiles: FiscalProfile[];
  }>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = ['description', 'sat', 'quantity', 'amount'];
  protected readonly eligibleSales = this.data.sales.filter(
    (sale) => sale.status === 'CONFIRMED' && sale.paymentStatus === 'PAID' && sale.customerId,
  );
  protected readonly form = this.fb.nonNullable.group({
    salesOrderId: ['', Validators.required],
    issuerProfileId: ['', Validators.required],
    fiscalProfileId: ['', Validators.required],
    series: ['', Validators.maxLength(25)],
    folio: ['', Validators.maxLength(50)],
    paymentFormCode: ['01', Validators.maxLength(5)],
    paymentMethodCode: ['PUE', Validators.maxLength(5)],
  });
  protected selectedSaleChanged(salesOrderId: string): void {
    const sale = this.eligibleSales.find((item) => item.salesOrderId === salesOrderId);
    const profile = this.data.profiles.find(
      (item) => item.customerId === sale?.customerId && item.status === 'ACTIVE',
    );
    const issuer = this.data.issuers.find(
      (item) => item.branchId === sale?.branchId && item.status === 'ACTIVE',
    );
    this.form.patchValue({
      fiscalProfileId: profile?.fiscalProfileId ?? '',
      issuerProfileId: issuer?.issuerProfileId ?? '',
      series: issuer?.defaultSeries ?? '',
    });
  }
  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api
      .createFiscalDocument({
        salesOrderId: value.salesOrderId,
        issuerProfileId: value.issuerProfileId,
        fiscalProfileId: value.fiscalProfileId,
        series: value.series.trim() || null,
        folio: value.folio.trim() || null,
        paymentFormCode: value.paymentFormCode.trim().toUpperCase() || null,
        paymentMethodCode: value.paymentMethodCode.trim().toUpperCase() || null,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (document) => this.ref.close(document),
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible crear el documento fiscal.'),
          ),
      });
  }
}
