import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { Category, CategoryStatus, CategoryTreeNode } from '../models/category.model';

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CategoryListFilters {
  search?: string;
  status?: CategoryStatus;
}

@Injectable({
  providedIn: 'root',
})
export class CategoryApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  list(filters: CategoryListFilters = {}): Observable<Category[]> {
    let params = new HttpParams().set('size', 100);

    if (filters.search) {
      params = params.set('search', filters.search);
    }

    if (filters.status) {
      params = params.set('status', filters.status);
    }

    return this.http
      .get<ApiResponse<PageResponse<Category>>>(`${this.apiConfig.baseUrl}/catalog/categories`, {
        params,
      })
      .pipe(map((response) => response.data?.content ?? []));
  }

  tree(status?: CategoryStatus): Observable<CategoryTreeNode[]> {
    let params = new HttpParams();

    if (status) {
      params = params.set('status', status);
    }

    return this.http
      .get<ApiResponse<CategoryTreeNode[]>>(`${this.apiConfig.baseUrl}/catalog/categories/tree`, {
        params,
      })
      .pipe(map((response) => response.data ?? []));
  }
}
