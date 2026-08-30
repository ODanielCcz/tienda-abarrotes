import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import {
  ConfirmSalesReturnRequest,
  CreateSalesReturnRequest,
  SalesReturn,
} from '../models/sales-return.model';

@Injectable({ providedIn: 'root' })
export class SalesReturnApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  listByOrder(salesOrderId: string): Observable<SalesReturn[]> {
    return this.http
      .get<ApiResponse<SalesReturn[]>>(
        `${this.config.baseUrl}/sales/orders/${salesOrderId}/returns`,
      )
      .pipe(map((response) => response.data ?? []));
  }

  create(salesOrderId: string, request: CreateSalesReturnRequest): Observable<SalesReturn> {
    return this.require(
      this.http.post<ApiResponse<SalesReturn>>(
        `${this.config.baseUrl}/sales/orders/${salesOrderId}/returns`,
        request,
      ),
    );
  }

  get(returnId: string): Observable<SalesReturn> {
    return this.require(
      this.http.get<ApiResponse<SalesReturn>>(`${this.config.baseUrl}/sales/returns/${returnId}`),
    );
  }

  confirm(returnId: string, request: ConfirmSalesReturnRequest): Observable<SalesReturn> {
    return this.require(
      this.http.post<ApiResponse<SalesReturn>>(
        `${this.config.baseUrl}/sales/returns/${returnId}/confirm`,
        request,
      ),
    );
  }

  cancel(returnId: string): Observable<SalesReturn> {
    return this.require(
      this.http.post<ApiResponse<SalesReturn>>(
        `${this.config.baseUrl}/sales/returns/${returnId}/cancel`,
        {},
      ),
    );
  }

  private require(request: Observable<ApiResponse<SalesReturn>>): Observable<SalesReturn> {
    return request.pipe(
      map((response) => {
        if (response.data === null) throw new Error('La API respondió sin datos de la devolución.');
        return response.data;
      }),
    );
  }
}
