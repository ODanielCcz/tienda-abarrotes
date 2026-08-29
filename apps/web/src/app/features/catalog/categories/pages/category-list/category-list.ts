import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, filter, finalize, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { CategoryFormDialog } from '../../components/category-form-dialog/category-form-dialog';
import { CategoryApi } from '../../data-access/category-api.service';
import {
  Category,
  CategoryStatus,
  CategoryTreeNode,
} from '../../models/category.model';

@Component({
  selector: 'app-category-list',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './category-list.html',
  styleUrl: './category-list.scss',
})
export class CategoryList {
  private readonly categoryApi = inject(CategoryApi);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly displayedColumns = ['code', 'name', 'parent', 'status', 'createdAt', 'actions'];
  protected readonly categories = signal<Category[]>([]);
  protected readonly categoryTree = signal<CategoryTreeNode[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly statusChangingId = signal<string | null>(null);
  protected readonly viewMode = signal<'LIST' | 'TREE'>('LIST');
  protected readonly canCreate = this.sessionStore.hasPermission('CATALOG_CATEGORY_CREATE');
  protected readonly canUpdate = this.sessionStore.hasPermission('CATALOG_CATEGORY_UPDATE');
  protected readonly canChangeStatus = this.sessionStore.hasPermission('CATALOG_CATEGORY_STATUS');

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<CategoryStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });
  protected readonly viewModeControl = new FormControl<'LIST' | 'TREE'>('LIST', {
    nonNullable: true,
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadCategories());

    this.statusControl.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshData());

    this.viewModeControl.valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((mode) => {
        this.viewMode.set(mode);
        this.refreshData();
      });

    this.loadCategories();
  }

  protected refresh(): void {
    this.refreshData();
  }

  protected openForm(category: Category | null): void {
    this.errorMessage.set(null);
    this.categoryApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (categories) => {
          this.dialog
            .open(CategoryFormDialog, {
              width: '34rem',
              maxWidth: '95vw',
              disableClose: true,
              data: { category, categories },
            })
            .afterClosed()
            .pipe(
              filter((result): result is Category => result !== null && result !== undefined),
              takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(() => this.refreshData());
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible cargar las categorías padre.'),
          ),
      });
  }

  protected changeStatus(category: Category): void {
    const nextStatus: CategoryStatus = category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const action = nextStatus === 'ACTIVE' ? 'activar' : 'desactivar';

    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        maxWidth: '95vw',
        data: {
          title: `${nextStatus === 'ACTIVE' ? 'Activar' : 'Desactivar'} categoría`,
          message: `¿Deseas ${action} la categoría ${category.name}?`,
          confirmLabel: nextStatus === 'ACTIVE' ? 'Activar' : 'Desactivar',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.statusChangingId.set(category.id);
          this.errorMessage.set(null);
          return this.categoryApi
            .changeStatus(category.id, nextStatus)
            .pipe(finalize(() => this.statusChangingId.set(null)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.refreshData(),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible cambiar el estado.')),
      });
  }

  protected parentName(parentCategoryId: string | null): string {
    if (parentCategoryId === null) return 'Categoría raíz';
    return (
      this.categories().find((category) => category.id === parentCategoryId)?.name ??
      'Categoría no disponible'
    );
  }

  private refreshData(): void {
    if (this.viewMode() === 'TREE') {
      this.loadCategoryTree();
      return;
    }

    this.loadCategories();
  }

  private loadCategories(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const status = this.statusControl.value === 'ALL' ? undefined : this.statusControl.value;

    this.categoryApi
      .list({
        search: this.searchControl.value.trim() || undefined,
        status,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (categories) => this.categories.set(categories),
        error: (error: unknown) => {
          this.categories.set([]);
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar las categorías.'));
        },
      });
  }

  private loadCategoryTree(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    const status = this.statusControl.value === 'ALL' ? undefined : this.statusControl.value;

    this.categoryApi
      .tree(status)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (tree) => this.categoryTree.set(tree),
        error: (error: unknown) => {
          this.categoryTree.set([]);
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible consultar el árbol de categorías.'),
          );
        },
      });
  }
}
