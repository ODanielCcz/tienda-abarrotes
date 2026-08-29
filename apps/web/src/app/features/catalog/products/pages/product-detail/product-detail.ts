import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap, tap } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { BrandApi } from '../../../brands/data-access/brand-api';
import { Brand } from '../../../brands/models/brand.model';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { Category } from '../../../categories/models/category.model';
import { ProductApi } from '../../data-access/product-api';
import { Product, ProductPresentation } from '../../models/product.model';

@Component({
  selector: 'app-product-detail',
  imports: [
    DatePipe,
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
  ],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  private readonly productApi = inject(ProductApi);
  private readonly brandApi = inject(BrandApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload = new Subject<void>();
  private readonly productId = this.route.snapshot.paramMap.get('productId') ?? '';

  protected readonly backQueryParams = this.route.snapshot.queryParams;
  protected readonly product = signal<Product | null>(null);
  protected readonly presentations = signal<ProductPresentation[]>([]);
  protected readonly brands = signal<Brand[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly displayedColumns = [
    'name',
    'sku',
    'unit',
    'tax',
    'conversionFactor',
    'netContent',
    'minimumStock',
    'status',
    'updatedAt',
  ];

  constructor() {
    this.reload
      .pipe(
        startWith(undefined),
        tap(() => {
          this.loading.set(true);
          this.errorMessage.set(null);
        }),
        switchMap(() =>
          forkJoin({
            product: this.productApi.getById(this.productId),
            presentations: this.productApi.listPresentations(this.productId),
            brands: this.brandApi.list(),
            categories: this.categoryApi.list(),
          }).pipe(
            map((data) => ({ ok: true as const, data })),
            catchError((error: unknown) =>
              of({
                ok: false as const,
                message: apiErrorMessage(
                  error,
                  'No fue posible consultar el detalle del producto.',
                ),
              }),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.loading.set(false);
        if (!result.ok) {
          this.product.set(null);
          this.presentations.set([]);
          this.errorMessage.set(result.message);
          return;
        }

        this.product.set(result.data.product);
        this.presentations.set(result.data.presentations);
        this.brands.set(result.data.brands);
        this.categories.set(result.data.categories);
      });
  }

  protected refresh(): void {
    this.reload.next();
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
}
