import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, finalize, startWith } from 'rxjs';

import { CategoryApi } from '../../data-access/category-api.service';
import { Category, CategoryStatus } from '../../models/category.model';

@Component({
  selector: 'app-category-list',
  imports: [
    DatePipe,
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

  protected readonly displayedColumns = ['code', 'name', 'parent', 'status', 'createdAt'];
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<CategoryStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(startWith(this.searchControl.value), debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.loadCategories());

    this.statusControl.valueChanges
      .pipe(startWith(this.statusControl.value), distinctUntilChanged())
      .subscribe(() => this.loadCategories());
  }

  protected refresh(): void {
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
        error: () => {
          this.categories.set([]);
          this.errorMessage.set('No fue posible consultar las categorías.');
        },
      });
  }
}
