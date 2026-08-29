import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { CatalogTax, CatalogUnit } from '../models/catalog-reference.model';

@Injectable({ providedIn: 'root' })
export class CatalogReferenceApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  listUnits(): Observable<CatalogUnit[]> {
    return this.http
      .get<ApiResponse<CatalogUnit[]>>(`${this.apiConfig.baseUrl}/catalog/units`)
      .pipe(map((response) => response.data ?? []));
  }

  listTaxes(): Observable<CatalogTax[]> {
    return this.http
      .get<ApiResponse<CatalogTax[]>>(`${this.apiConfig.baseUrl}/catalog/taxes`)
      .pipe(map((response) => response.data ?? []));
  }
}
