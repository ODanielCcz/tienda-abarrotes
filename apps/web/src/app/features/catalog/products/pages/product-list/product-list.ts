import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, ParamMap, RouterLink } from '@angular/router';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  merge,
  of,
  startWith,
  Subject,
  switchMap,
  tap,
} from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { BrandApi } from '../../../brands/data-access/brand-api';
import { Brand } from '../../../brands/models/brand.model';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { Category } from '../../../categories/models/category.model';
import { ProductApi } from '../../data-access/product-api';
import { Product, ProductStatus } from '../../models/product.model';

export interface RestoredProductListState {
  page: number;
  size: number;
  search?: string;
  status?: ProductStatus;
  brandId?: string;
  categoryId?: string;
}

export function readProductListQueryParams(params: ParamMap): RestoredProductListState {
  const pageCandidate = Number(params.get('page'));
  const sizeCandidate = Number(params.get('size'));
  const statusCandidate = params.get('status');
  const search = params.get('search')?.trim() || undefined;
  const brandId = params.get('brandId')?.trim() || undefined;
  const categoryId = params.get('categoryId')?.trim() || undefined;

  return {
    page: Number.isInteger(pageCandidate) && pageCandidate >= 0 ? pageCandidate : 0,
    size: [10, 20, 50].includes(sizeCandidate) ? sizeCandidate : 20,
    search,
    status:
      statusCandidate === 'ACTIVE' ||
      statusCandidate === 'INACTIVE' ||
      statusCandidate === 'DISCONTINUED'
        ? statusCandidate
        : undefined,
    brandId,
    categoryId,
  };
}

@Component({
  selector: 'app-product-list',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly productApi = inject(ProductApi);
  private readonly brandApi = inject(BrandApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly queryTrigger = new Subject<void>();

  protected readonly products = signal<Product[]>([]);
  protected readonly brands = signal<Brand[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly totalElements = signal(0);
  protected readonly displayedColumns = [
    'name',
    'brand',
    'category',
    'type',
    'policies',
    'status',
    'updatedAt',
    'actions',
  ];

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<ProductStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });
  protected readonly brandControl = new FormControl('ALL', { nonNullable: true });
  protected readonly categoryControl = new FormControl('ALL', { nonNullable: true });

  constructor() {
    const restored = readProductListQueryParams(this.route.snapshot.queryParamMap);
    this.pageIndex.set(restored.page);
    this.pageSize.set(restored.size);
    this.searchControl.setValue(restored.search ?? '', { emitEvent: false });
    this.statusControl.setValue(restored.status ?? 'ALL', { emitEvent: false });
    this.brandControl.setValue(restored.brandId ?? 'ALL', { emitEvent: false });
    this.categoryControl.setValue(restored.categoryId ?? 'ALL', { emitEvent: false });

    merge(
      this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.statusControl.valueChanges.pipe(distinctUntilChanged()),
      this.brandControl.valueChanges.pipe(distinctUntilChanged()),
      this.categoryControl.valueChanges.pipe(distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.queryTrigger.next();
      });

    this.queryTrigger
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() =>
          this.productApi.list(this.currentFilters()).pipe(
            map((page) => ({ ok: true as const, page })),
            catchError((error: unknown) =>
              of({
                ok: false as const,
                message: apiErrorMessage(error, 'No fue posible consultar los productos.'),
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (!result.ok) {
          this.products.set([]);
          this.totalElements.set(0);
          this.errorMessage.set(result.message);
          return;
        }

        this.products.set(result.page.content);
        this.totalElements.set(result.page.totalElements);
      });

    this.brandApi
      .list({ status: 'ACTIVE' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((brands) => this.brands.set(brands));
    this.categoryApi
      .list({ status: 'ACTIVE' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((categories) => this.categories.set(categories));
  }

  protected refresh(): void {
    this.queryTrigger.next();
  }

  protected pageChanged(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.queryTrigger.next();
  }

  protected clearFilters(): void {
    this.searchControl.setValue('', { emitEvent: false });
    this.statusControl.setValue('ALL', { emitEvent: false });
    this.brandControl.setValue('ALL', { emitEvent: false });
    this.categoryControl.setValue('ALL', { emitEvent: false });
    this.pageIndex.set(0);
    this.queryTrigger.next();
  }

  protected brandName(brandId: string | null): string {
    if (brandId === null) return 'Sin marca';
    return this.brands().find((brand) => brand.id === brandId)?.name ?? 'Marca no disponible';
  }

  protected categoryName(categoryId: string): string {
    return (
      this.categories().find((category) => category.id === categoryId)?.name ??
      'Categoría no disponible'
    );
  }

  protected currentQueryParams(): Record<string, string | number | undefined> {
    const filters = this.currentFilters();
    return {
      page: filters.page,
      size: filters.size,
      search: filters.search,
      status: filters.status,
      brandId: filters.brandId,
      categoryId: filters.categoryId,
      sortBy: filters.sortBy,
      direction: filters.direction,
    };
  }

  private currentFilters() {
    return {
      page: this.pageIndex(),
      size: this.pageSize(),
      search: this.searchControl.value.trim() || undefined,
      status: this.statusControl.value === 'ALL' ? undefined : this.statusControl.value,
      brandId: this.brandControl.value === 'ALL' ? undefined : this.brandControl.value,
      categoryId: this.categoryControl.value === 'ALL' ? undefined : this.categoryControl.value,
      sortBy: 'NAME' as const,
      direction: 'ASC' as const,
    };
  }
}
