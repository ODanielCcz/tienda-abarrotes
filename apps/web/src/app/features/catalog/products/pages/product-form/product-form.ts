import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { BrandApi } from '../../../brands/data-access/brand-api';
import { Brand } from '../../../brands/models/brand.model';
import { CategoryApi } from '../../../categories/data-access/category-api.service';
import { Category } from '../../../categories/models/category.model';
import { ProductApi } from '../../data-access/product-api';
import { ProductUpsertRequest } from '../../models/product-request.model';
import { ProductType } from '../../models/product.model';

export interface ProductTrackingValues {
  productType: ProductType;
  tracksInventory: boolean;
  tracksLots: boolean;
  tracksExpiration: boolean;
}

export function productTrackingError(values: ProductTrackingValues): string | null {
  if (values.productType === 'SERVICE' && (values.tracksInventory || values.tracksLots || values.tracksExpiration)) {
    return 'Un servicio no puede controlar inventario, lotes o caducidad.';
  }
  if (!values.tracksInventory && (values.tracksLots || values.tracksExpiration)) {
    return 'Un producto sin inventario no puede controlar lotes o caducidad.';
  }
  if (values.tracksExpiration && !values.tracksLots) {
    return 'Un producto con caducidad debe controlar lotes.';
  }
  return null;
}

const productTrackingValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const error = productTrackingError(control.value as ProductTrackingValues);
  return error === null ? null : { trackingRules: error };
};

@Component({
  selector: 'app-product-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm {
  private readonly productApi = inject(ProductApi);
  private readonly brandApi = inject(BrandApi);
  private readonly categoryApi = inject(CategoryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  private readonly productId = this.route.snapshot.paramMap.get('productId');

  protected readonly editing = this.productId !== null;
  protected readonly brands = signal<Brand[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.group(
    {
      categoryId: this.formBuilder.control<string | null>(null),
      brandId: this.formBuilder.control<string | null>(null),
      name: this.formBuilder.nonNullable.control('', [
        Validators.required,
        Validators.maxLength(200),
      ]),
      description: this.formBuilder.nonNullable.control('', [Validators.maxLength(1000)]),
      productType: this.formBuilder.nonNullable.control<ProductType>('GOODS'),
      tracksInventory: this.formBuilder.nonNullable.control(true),
      tracksLots: this.formBuilder.nonNullable.control(false),
      tracksExpiration: this.formBuilder.nonNullable.control(false),
    },
    { validators: [productTrackingValidator] },
  );

  constructor() {
    forkJoin({
      brands: this.brandApi.list(),
      categories: this.categoryApi.list(),
      product: this.productId === null ? of(null) : this.productApi.getById(this.productId),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ brands, categories, product }) => {
          this.brands.set(brands);
          this.categories.set(categories);
          if (product !== null) {
            this.form.setValue({
              categoryId: product.categoryId,
              brandId: product.brandId,
              name: product.name,
              description: product.description ?? '',
              productType: product.productType,
              tracksInventory: product.tracksInventory,
              tracksLots: product.tracksLots,
              tracksExpiration: product.tracksExpiration,
            });
          }
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible preparar el formulario del producto.'),
          ),
      });
  }

  protected submit(): void {
    const raw = this.form.getRawValue();
    const payload: ProductUpsertRequest = {
      categoryId: raw.categoryId,
      brandId: raw.brandId,
      name: raw.name.trim(),
      description: raw.description.trim() || null,
      productType: raw.productType,
      tracksInventory: raw.tracksInventory,
      tracksLots: raw.tracksLots,
      tracksExpiration: raw.tracksExpiration,
    };
    this.form.patchValue({ name: payload.name, description: payload.description ?? '' });
    this.form.markAllAsTouched();

    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const request$ = this.productId === null
      ? this.productApi.create(payload)
      : this.productApi.update(this.productId, payload);

    request$
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (product) => void this.router.navigate(['/catalog/products', product.id]),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el producto.')),
      });
  }

  protected trackingError(): string | null {
    return (this.form.errors?.['trackingRules'] as string | undefined) ?? null;
  }
}
