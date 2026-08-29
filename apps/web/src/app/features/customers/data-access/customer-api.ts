import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  Customer,
  CustomerFilters,
  CustomerRequest,
  CustomerStatus,
} from '../models/customer.model';
@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);
  list(filters: CustomerFilters = {}): Observable<Customer[]> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.customerType) params = params.set('customerType', filters.customerType);
    if (filters.status) params = params.set('status', filters.status);
    return this.http
      .get<ApiResponse<Customer[]>>(`${this.config.baseUrl}/sales/customers`, { params })
      .pipe(map((r) => r.data ?? []));
  }
  create(request: CustomerRequest): Observable<Customer> {
    return this.require(
      this.http.post<ApiResponse<Customer>>(`${this.config.baseUrl}/sales/customers`, request),
    );
  }
  update(id: string, request: CustomerRequest): Observable<Customer> {
    return this.require(
      this.http.put<ApiResponse<Customer>>(`${this.config.baseUrl}/sales/customers/${id}`, request),
    );
  }
  changeStatus(id: string, status: CustomerStatus): Observable<Customer> {
    return this.require(
      this.http.patch<ApiResponse<Customer>>(
        `${this.config.baseUrl}/sales/customers/${id}/status`,
        { status },
      ),
    );
  }
  private require(request: Observable<ApiResponse<Customer>>): Observable<Customer> {
    return request.pipe(
      map((r) => {
        if (r.data === null) throw new Error('La API respondió sin datos del cliente.');
        return r.data;
      }),
    );
  }
}
