import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../../core/config/api.config';
import { ApiResponse } from '../../../../shared/models/api-response.model';
import { CreateSalesPaymentRequest, SalesPayment } from '../models/sales-payment.model';

@Injectable({ providedIn: 'root' })
export class SalesPaymentApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  list(salesOrderId: string): Observable<SalesPayment[]> {
    return this.http
      .get<ApiResponse<SalesPayment[]>>(
        `${this.config.baseUrl}/sales/orders/${salesOrderId}/payments`,
      )
      .pipe(map((response) => response.data ?? []));
  }

  create(salesOrderId: string, request: CreateSalesPaymentRequest): Observable<SalesPayment> {
    return this.require(
      this.http.post<ApiResponse<SalesPayment>>(
        `${this.config.baseUrl}/sales/orders/${salesOrderId}/payments`,
        request,
      ),
    );
  }

  get(paymentId: string): Observable<SalesPayment> {
    return this.require(
      this.http.get<ApiResponse<SalesPayment>>(
        `${this.config.baseUrl}/sales/payments/${paymentId}`,
      ),
    );
  }

  cancel(paymentId: string): Observable<SalesPayment> {
    return this.require(
      this.http.post<ApiResponse<SalesPayment>>(
        `${this.config.baseUrl}/sales/payments/${paymentId}/cancel`,
        {},
      ),
    );
  }

  private require(request: Observable<ApiResponse<SalesPayment>>): Observable<SalesPayment> {
    return request.pipe(
      map((response) => {
        if (response.data === null) throw new Error('La API respondió sin datos del pago.');
        return response.data;
      }),
    );
  }
}
