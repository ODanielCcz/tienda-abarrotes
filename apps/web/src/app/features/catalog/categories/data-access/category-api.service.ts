import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { CategoryUpsertRequest } from '../models/category-request.model';
import { Category, CategoryStatus, CategoryTreeNode } from '../models/category.model';

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

  create(request: CategoryUpsertRequest): Observable<Category> {
    return this.http
      .post<ApiResponse<Category>>(`${this.apiConfig.baseUrl}/catalog/categories`, request)
      .pipe(map((response) => this.requireCategory(response)));
  }

  update(categoryId: string, request: CategoryUpsertRequest): Observable<Category> {
    return this.http
      .put<ApiResponse<Category>>(
        `${this.apiConfig.baseUrl}/catalog/categories/${categoryId}`,
        request,
      )
      .pipe(map((response) => this.requireCategory(response)));
  }

  changeStatus(categoryId: string, status: CategoryStatus): Observable<Category> {
    return this.http
      .patch<ApiResponse<Category>>(
        `${this.apiConfig.baseUrl}/catalog/categories/${categoryId}/status`,
        { status },
      )
      .pipe(map((response) => this.requireCategory(response)));
  }

  private requireCategory(response: ApiResponse<Category>): Category {
    if (response.data === null) {
      throw new Error('La API respondió sin datos de la categoría.');
    }
    return response.data;
  }
}
