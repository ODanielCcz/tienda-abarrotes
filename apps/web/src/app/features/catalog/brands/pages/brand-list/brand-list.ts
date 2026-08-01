import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, distinctUntilChanged, finalize, startWith } from 'rxjs';

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

  protected readonly displayedColumns = ['code', 'name', 'status', 'createdAt'];
  protected readonly brands = signal<Brand[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly searchControl = new FormControl('', { nonNullable: true });
  protected readonly statusControl = new FormControl<BrandStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(startWith(this.searchControl.value), debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.loadBrands());

    this.statusControl.valueChanges
      .pipe(startWith(this.statusControl.value), distinctUntilChanged())
      .subscribe(() => this.loadBrands());
  }

  protected refresh(): void {
    this.loadBrands();
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
        error: () => {
          this.brands.set([]);
          this.errorMessage.set('No fue posible consultar las marcas.');
        },
      });
  }
}
