import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { filter, finalize, forkJoin, map, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { CatalogReferenceApi } from '../../../catalog/products/data-access/catalog-reference-api';
import { ProductApi } from '../../../catalog/products/data-access/product-api';
import { CatalogUnit } from '../../../catalog/products/models/catalog-reference.model';
import { Product } from '../../../catalog/products/models/product.model';
import { CustomerApi } from '../../../customers/data-access/customer-api';
import { Customer } from '../../../customers/models/customer.model';
import { OrganizationApi } from '../../../organization/data-access/organization-api';
import { Branch } from '../../../organization/models/organization.model';
import { SalesApi } from '../../../sales/data-access/sales-api';
import { SalesOrder } from '../../../sales/models/sales.model';
import { FiscalClassificationDialog } from '../../components/fiscal-classification-dialog/fiscal-classification-dialog';
import { FiscalDocumentDialog } from '../../components/fiscal-document-dialog/fiscal-document-dialog';
import { FiscalProfileDialog } from '../../components/fiscal-profile-dialog/fiscal-profile-dialog';
import { IssuerProfileDialog } from '../../components/issuer-profile-dialog/issuer-profile-dialog';
import { BillingApi } from '../../data-access/billing-api';
import { FiscalDocument, FiscalProfile, IssuerProfile } from '../../models/billing.model';

@Component({
  selector: 'app-billing-overview',
  imports: [
    CurrencyPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './billing-overview.html',
  styleUrl: './billing-overview.scss',
})
export class BillingOverview {
  private readonly api = inject(BillingApi);
  private readonly organizationApi = inject(OrganizationApi);
  private readonly customerApi = inject(CustomerApi);
  private readonly salesApi = inject(SalesApi);
  private readonly productApi = inject(ProductApi);
  private readonly referenceApi = inject(CatalogReferenceApi);
  private readonly session = inject(AuthSessionStore);
  private readonly context = inject(OperationalContextStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly issuers = signal<IssuerProfile[]>([]);
  protected readonly profiles = signal<FiscalProfile[]>([]);
  protected readonly documents = signal<FiscalDocument[]>([]);
  protected readonly branches = signal<Branch[]>([]);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly sales = signal<SalesOrder[]>([]);
  protected readonly products = signal<Product[]>([]);
  protected readonly units = signal<CatalogUnit[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly profileColumns = ['rfc', 'name', 'owner', 'regime', 'status', 'actions'];
  protected readonly documentColumns = [
    'folio',
    'sale',
    'issuer',
    'receiver',
    'total',
    'status',
    'actions',
  ];
  protected readonly canReadIssuers = this.can('BILLING_ISSUER_PROFILE_READ');
  protected readonly canReadProfiles = this.can('BILLING_FISCAL_PROFILE_READ');
  protected readonly canReadDocuments = this.can('BILLING_FISCAL_DOCUMENT_READ');

  constructor() {
    this.load();
  }
  protected can(permission: string): boolean {
    return this.session.hasPermission(permission);
  }

  protected openIssuer(profile: IssuerProfile | null): void {
    this.dialog
      .open(IssuerProfileDialog, {
        width: '52rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { profile, branches: this.branches() },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }
  protected openProfile(profile: FiscalProfile | null): void {
    this.dialog
      .open(FiscalProfileDialog, {
        width: '52rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { profile, customers: this.customers() },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }
  protected openDocument(): void {
    this.dialog
      .open(FiscalDocumentDialog, {
        width: '60rem',
        maxWidth: '96vw',
        disableClose: true,
        data: {
          document: null,
          sales: this.sales(),
          issuers: this.issuers(),
          profiles: this.profiles(),
        },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }
  protected viewDocument(document: FiscalDocument): void {
    this.api
      .getFiscalDocument(document.fiscalDocumentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) =>
          this.dialog.open(FiscalDocumentDialog, {
            width: '60rem',
            maxWidth: '96vw',
            data: { document: detail, sales: [], issuers: [], profiles: [] },
          }),
        error: (error) => this.showError(error),
      });
  }
  protected markReady(document: FiscalDocument): void {
    this.dialog
      .open(ConfirmationDialog, {
        width: '30rem',
        data: {
          title: 'Marcar documento como listo',
          message: 'El snapshot fiscal quedará inmutable. Esta acción no timbra un CFDI.',
          confirmLabel: 'Marcar READY',
        },
      })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.markReady(document.fiscalDocumentId)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: () => this.load(), error: (error) => this.showError(error) });
  }
  protected toggleIssuer(profile: IssuerProfile): void {
    this.api
      .changeIssuerStatus(
        profile.issuerProfileId,
        profile.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      )
      .subscribe({ next: () => this.load(), error: (error) => this.showError(error) });
  }
  protected toggleProfile(profile: FiscalProfile): void {
    this.api
      .changeFiscalProfileStatus(
        profile.fiscalProfileId,
        profile.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
      )
      .subscribe({ next: () => this.load(), error: (error) => this.showError(error) });
  }
  protected classify(mode: 'PRODUCT' | 'UNIT'): void {
    this.dialog.open(FiscalClassificationDialog, {
      width: '46rem',
      maxWidth: '95vw',
      disableClose: true,
      data: { mode, products: this.products(), units: this.units() },
    });
  }
  protected branchName(id: string): string {
    return this.branches().find((item) => item.branchId === id)?.name ?? id;
  }
  protected customerName(id: string): string {
    return this.customers().find((item) => item.customerId === id)?.displayName ?? id;
  }
  protected refresh(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      issuers: this.canReadIssuers ? this.api.listIssuerProfiles() : of([]),
      profiles: this.canReadProfiles ? this.api.listFiscalProfiles() : of([]),
      documents: this.canReadDocuments ? this.api.listFiscalDocuments() : of([]),
      branches: this.can('ORGANIZATION_BRANCH_READ') ? this.organizationApi.listBranches() : of([]),
      customers: this.can('SALES_CUSTOMER_READ') ? this.customerApi.list() : of([]),
      sales: this.can('SALES_ORDER_READ')
        ? this.salesApi.list({ warehouseId: this.context.selectedWarehouse()?.warehouseId })
        : of([]),
      products: this.can('CATALOG_PRODUCT_READ')
        ? this.productApi
            .list({ page: 0, size: 100, sortBy: 'NAME', direction: 'ASC' })
            .pipe(map((page) => page.content))
        : of([]),
      units: this.can('CATALOG_PRESENTATION_READ') ? this.referenceApi.listUnits() : of([]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => {
          this.issuers.set(data.issuers);
          this.profiles.set(data.profiles);
          this.documents.set(data.documents);
          this.branches.set(data.branches);
          this.customers.set(data.customers);
          this.sales.set(data.sales);
          this.products.set(data.products);
          this.units.set(data.units);
        },
        error: (error) => this.showError(error),
      });
  }
  private showError(error: unknown): void {
    this.loading.set(false);
    this.errorMessage.set(apiErrorMessage(error, 'No fue posible completar la operación fiscal.'));
  }
}
