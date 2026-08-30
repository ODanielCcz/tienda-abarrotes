import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { CreateSalesOrderRequest, SalesOrder, SalesOrderFilters } from '../models/sales.model';

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  list(filters: SalesOrderFilters = {}): Observable<SalesOrder[]> {
    let params = new HttpParams();
    if (filters.warehouseId) params = params.set('warehouseId', filters.warehouseId);
    if (filters.customerId) params = params.set('customerId', filters.customerId);
    if (filters.status) params = params.set('status', filters.status);
    return this.http
      .get<ApiResponse<SalesOrder[]>>(`${this.config.baseUrl}/sales/orders`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  create(request: CreateSalesOrderRequest): Observable<SalesOrder> {
    return this.require(
      this.http.post<ApiResponse<SalesOrder>>(`${this.config.baseUrl}/sales/orders`, request),
    );
  }

  get(salesOrderId: string): Observable<SalesOrder> {
    return this.require(
      this.http.get<ApiResponse<SalesOrder>>(`${this.config.baseUrl}/sales/orders/${salesOrderId}`),
    );
  }

  cancel(salesOrderId: string): Observable<SalesOrder> {
    return this.require(
      this.http.post<ApiResponse<SalesOrder>>(
        `${this.config.baseUrl}/sales/orders/${salesOrderId}/cancel`,
        {},
      ),
    );
  }

  private require(request: Observable<ApiResponse<SalesOrder>>): Observable<SalesOrder> {
    return request.pipe(
      map((response) => {
        if (response.data === null) {
          throw new Error('La API respondió sin datos de la venta.');
        }
        return response.data;
      }),
    );
  }
}
