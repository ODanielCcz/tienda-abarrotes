import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, filter, finalize, merge, switchMap } from 'rxjs';
import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { CustomerFormDialog } from '../../components/customer-form-dialog/customer-form-dialog';
import { CustomerApi } from '../../data-access/customer-api';
import { Customer, CustomerStatus } from '../../models/customer.model';
@Component({
  selector: 'app-customer-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './customer-list.html',
  styleUrl: './customer-list.scss',
})
export class CustomerList {
  private readonly api = inject(CustomerApi);
  private readonly session = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly customers = signal<Customer[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = [
    'code',
    'name',
    'type',
    'contact',
    'status',
    'createdAt',
    'actions',
  ];
  protected readonly canCreate = this.session.hasPermission('SALES_CUSTOMER_CREATE');
  protected readonly canUpdate = this.session.hasPermission('SALES_CUSTOMER_UPDATE');
  protected readonly canStatus = this.session.hasPermission('SALES_CUSTOMER_STATUS');
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<CustomerStatus | 'ALL'>('ALL', { nonNullable: true });
  constructor() {
    merge(
      this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.status.valueChanges.pipe(distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
    this.load();
  }
  protected open(customer: Customer | null): void {
    this.dialog
      .open(CustomerFormDialog, {
        width: '44rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { customer },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }
  protected changeStatus(customer: Customer): void {
    const status: CustomerStatus = customer.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: {
          title: 'Cambiar estado del cliente',
          message: `¿Deseas cambiar el estado de ${customer.displayName}?`,
          confirmLabel: 'Confirmar',
        },
      })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.changeStatus(customer.customerId, status)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: () => this.load(), error: (e: unknown) => this.showError(e) });
  }
  protected refresh(): void {
    this.load();
  }
  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .list({
        search: this.search.value.trim() || undefined,
        status: this.status.value === 'ALL' ? undefined : this.status.value,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: (v) => this.customers.set(v), error: (e: unknown) => this.showError(e) });
  }
  private showError(e: unknown): void {
    this.errorMessage.set(apiErrorMessage(e, 'No fue posible consultar los clientes.'));
  }
}
