import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { ProductUpsertRequest } from '../models/product-request.model';
import {
  Product,
  ProductListFilters,
  ProductPresentation,
  ProductStatus,
} from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  list(filters: ProductListFilters): Observable<PageResponse<Product>> {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .set('sortBy', filters.sortBy ?? 'NAME')
      .set('direction', filters.direction ?? 'ASC');

    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.brandId) params = params.set('brandId', filters.brandId);

    return this.http
      .get<ApiResponse<PageResponse<Product>>>(`${this.apiConfig.baseUrl}/catalog/products`, {
        params,
      })
      .pipe(
        map(
          (response) =>
            response.data ?? {
              content: [],
              page: filters.page,
              size: filters.size,
              totalElements: 0,
              totalPages: 0,
              first: true,
              last: true,
            },
        ),
      );
  }

  getById(productId: string): Observable<Product> {
    return this.http
      .get<ApiResponse<Product>>(`${this.apiConfig.baseUrl}/catalog/products/${productId}`)
      .pipe(
        map((response) => {
          if (response.data === null) {
            throw new Error('La API respondió sin datos del producto.');
          }
          return response.data;
        }),
      );
  }

  listPresentations(productId: string): Observable<ProductPresentation[]> {
    return this.http
      .get<ApiResponse<ProductPresentation[]>>(
        `${this.apiConfig.baseUrl}/catalog/products/${productId}/presentations`,
      )
      .pipe(map((response) => response.data ?? []));
  }

  create(request: ProductUpsertRequest): Observable<Product> {
    return this.http
      .post<ApiResponse<Product>>(`${this.apiConfig.baseUrl}/catalog/products`, request)
      .pipe(map((response) => this.requireProduct(response)));
  }

  update(productId: string, request: ProductUpsertRequest): Observable<Product> {
    return this.http
      .put<ApiResponse<Product>>(`${this.apiConfig.baseUrl}/catalog/products/${productId}`, request)
      .pipe(map((response) => this.requireProduct(response)));
  }

  changeStatus(productId: string, status: ProductStatus): Observable<Product> {
    return this.http
      .patch<ApiResponse<Product>>(`${this.apiConfig.baseUrl}/catalog/products/${productId}/status`, {
        status,
      })
      .pipe(map((response) => this.requireProduct(response)));
  }

  private requireProduct(response: ApiResponse<Product>): Product {
    if (response.data === null) {
      throw new Error('La API respondió sin datos del producto.');
    }
    return response.data;
  }
}
