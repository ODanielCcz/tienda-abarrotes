import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  ActiveStatus,
  Branch,
  CashRegister,
  CashRegisterStatus,
  Warehouse,
} from '../models/organization.model';

@Injectable({ providedIn: 'root' })
export class OrganizationApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  listBranches(status?: ActiveStatus): Observable<Branch[]> {
    const params = status === undefined ? undefined : new HttpParams().set('status', status);
    return this.http
      .get<ApiResponse<Branch[]>>(`${this.apiConfig.baseUrl}/organization/branches`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  listWarehouses(branchId?: string, status?: ActiveStatus): Observable<Warehouse[]> {
    let params = new HttpParams();
    if (branchId !== undefined) params = params.set('branchId', branchId);
    if (status !== undefined) params = params.set('status', status);
    return this.http
      .get<ApiResponse<Warehouse[]>>(`${this.apiConfig.baseUrl}/organization/warehouses`, {
        params,
      })
      .pipe(map((response) => response.data ?? []));
  }

  listCashRegisters(
    branchId?: string,
    status?: CashRegisterStatus,
  ): Observable<CashRegister[]> {
    let params = new HttpParams();
    if (branchId !== undefined) params = params.set('branchId', branchId);
    if (status !== undefined) params = params.set('status', status);
    return this.http
      .get<ApiResponse<CashRegister[]>>(
        `${this.apiConfig.baseUrl}/organization/cash-registers`,
        { params },
      )
      .pipe(map((response) => response.data ?? []));
  }
}
