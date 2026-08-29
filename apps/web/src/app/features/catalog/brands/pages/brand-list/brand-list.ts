import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, filter, finalize, merge, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { BrandFormDialog } from '../../components/brand-form-dialog/brand-form-dialog';
import { BrandApi } from '../../data-access/brand-api';
import { Brand, BrandStatus } from '../../models/brand.model';

@Component({
  selector: 'app-brand-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './brand-list.html',
  styleUrl: './brand-list.scss',
})
export class BrandList {
  private readonly brandApi = inject(BrandApi);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly displayedColumns = ['code', 'name', 'status', 'createdAt', 'actions'];
  protected readonly brands = signal<Brand[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly statusChangingId = signal<string | null>(null);
  protected readonly canCreate = this.sessionStore.hasPermission('CATALOG_BRAND_CREATE');
  protected readonly canUpdate = this.sessionStore.hasPermission('CATALOG_BRAND_UPDATE');
  protected readonly canChangeStatus = this.sessionStore.hasPermission('CATALOG_BRAND_STATUS');

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<BrandStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });

  constructor() {
    merge(
      this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.statusControl.valueChanges.pipe(distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadBrands());

    this.loadBrands();
  }

  protected refresh(): void {
    this.loadBrands();
  }

  protected openForm(brand: Brand | null): void {
    this.dialog
      .open(BrandFormDialog, {
        width: '32rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { brand },
      })
      .afterClosed()
      .pipe(
        filter((result): result is Brand => result !== null && result !== undefined),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.loadBrands());
  }

  protected changeStatus(brand: Brand): void {
    const nextStatus: BrandStatus = brand.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const action = nextStatus === 'ACTIVE' ? 'activar' : 'desactivar';

    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        maxWidth: '95vw',
        data: {
          title: `${nextStatus === 'ACTIVE' ? 'Activar' : 'Desactivar'} marca`,
          message: `¿Deseas ${action} la marca ${brand.name}?`,
          confirmLabel: nextStatus === 'ACTIVE' ? 'Activar' : 'Desactivar',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.statusChangingId.set(brand.id);
          this.errorMessage.set(null);
          return this.brandApi
            .changeStatus(brand.id, nextStatus)
            .pipe(finalize(() => this.statusChangingId.set(null)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.loadBrands(),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible cambiar el estado.')),
      });
  }

  private loadBrands(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const status = this.statusControl.value === 'ALL' ? undefined : this.statusControl.value;

    this.brandApi
      .list({
        search: this.searchControl.value.trim() || undefined,
        status,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (brands) => this.brands.set(brands),
        error: (error: unknown) => {
          this.brands.set([]);
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar las marcas.'));
        },
      });
  }
}
