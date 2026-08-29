import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { PageResponse } from '../../../../shared/models/page-response.model';
import { BrandUpsertRequest } from '../models/brand-request.model';
import { Brand, BrandStatus } from '../models/brand.model';

export interface BrandListFilters {
  search?: string;
  status?: BrandStatus;
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

  create(request: BrandUpsertRequest): Observable<Brand> {
    return this.http
      .post<ApiResponse<Brand>>(`${this.apiConfig.baseUrl}/catalog/brands`, request)
      .pipe(map((response) => this.requireBrand(response)));
  }

  update(brandId: string, request: BrandUpsertRequest): Observable<Brand> {
    return this.http
      .put<ApiResponse<Brand>>(`${this.apiConfig.baseUrl}/catalog/brands/${brandId}`, request)
      .pipe(map((response) => this.requireBrand(response)));
  }

  changeStatus(brandId: string, status: BrandStatus): Observable<Brand> {
    return this.http
      .patch<ApiResponse<Brand>>(`${this.apiConfig.baseUrl}/catalog/brands/${brandId}/status`, {
        status,
      })
      .pipe(map((response) => this.requireBrand(response)));
  }

  private requireBrand(response: ApiResponse<Brand>): Brand {
    if (response.data === null) {
      throw new Error('La API respondió sin datos de la marca.');
    }
    return response.data;
  }
}
