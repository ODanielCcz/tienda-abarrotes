import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { Brand, BrandStatus } from '../models/brand.model';

export interface BrandListFilters {
  search?: string;
  status?: BrandStatus;
}

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class BrandApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  list(filters: BrandListFilters = {}): Observable<Brand[]> {
    let params = new HttpParams().set('size', 100);

    if (filters.search) {
      params = params.set('search', filters.search);
    }

    if (filters.status) {
      params = params.set('status', filters.status);
    }

    return this.http
      .get<ApiResponse<PageResponse<Brand>>>(`${this.apiConfig.baseUrl}/catalog/brands`, { params })
      .pipe(map((response) => response.data?.content ?? []));
  }
}
