import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { Product, ProductListFilters } from '../models/product.model';

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
}
