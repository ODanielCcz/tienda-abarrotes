import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { CategoryApi } from '../../data-access/category-api.service';
import { Category } from '../../models/category.model';

export interface CategoryFormDialogData {
  category: Category | null;
  categories: Category[];
}

export function eligibleCategoryParents(
  categories: Category[],
  currentCategoryId: string | null,
): Category[] {
  if (currentCategoryId === null) return categories;

  const excludedIds = new Set([currentCategoryId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const category of categories) {
      if (
        category.parentCategoryId !== null &&
        excludedIds.has(category.parentCategoryId) &&
        !excludedIds.has(category.id)
      ) {
        excludedIds.add(category.id);
        changed = true;
      }
    }
  }

  return categories.filter((category) => !excludedIds.has(category.id));
}

@Component({
  selector: 'app-category-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './category-form-dialog.html',
  styleUrl: './category-form-dialog.scss',
})
export class CategoryFormDialog {
  private readonly categoryApi = inject(CategoryApi);
  private readonly dialogRef = inject(MatDialogRef<CategoryFormDialog, Category | null>);
  protected readonly data = inject<CategoryFormDialogData>(MAT_DIALOG_DATA);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly eligibleParents = eligibleCategoryParents(
    this.data.categories,
    this.data.category?.id ?? null,
  );
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.formBuilder.group({
    code: this.formBuilder.nonNullable.control(this.data.category?.code ?? '', [
      Validators.required,
      Validators.maxLength(50),
    ]),
    name: this.formBuilder.nonNullable.control(this.data.category?.name ?? '', [
      Validators.required,
      Validators.maxLength(150),
    ]),
    parentCategoryId: this.formBuilder.control<string | null>(
      this.data.category?.parentCategoryId ?? null,
    ),
  });

  protected submit(): void {
    const payload = {
      code: this.form.controls.code.value.trim().toUpperCase(),
      name: this.form.controls.name.value.trim(),
      parentCategoryId: this.form.controls.parentCategoryId.value,
    };
    this.form.setValue(payload, { emitEvent: false });
    this.form.markAllAsTouched();

    if (this.form.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const request$ = this.data.category
      ? this.categoryApi.update(this.data.category.id, payload)
      : this.categoryApi.create(payload);

    request$
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (category) => this.dialogRef.close(category),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar la categoría.')),
      });
  }
}
