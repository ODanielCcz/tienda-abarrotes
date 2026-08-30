import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CashMovement,
  CashSession,
  CashSessionFilters,
  CloseCashSessionRequest,
  CreateCashMovementRequest,
  OpenCashSessionRequest,
} from '../models/cash.model';

@Injectable({ providedIn: 'root' })
export class CashApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);

  listSessions(filters: CashSessionFilters = {}): Observable<CashSession[]> {
    let params = new HttpParams();
    if (filters.cashRegisterId) params = params.set('cashRegisterId', filters.cashRegisterId);
    if (filters.status) params = params.set('status', filters.status);
    return this.http
      .get<ApiResponse<CashSession[]>>(`${this.config.baseUrl}/cash/sessions`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  getSession(cashSessionId: string): Observable<CashSession> {
    return this.require(
      this.http.get<ApiResponse<CashSession>>(
        `${this.config.baseUrl}/cash/sessions/${cashSessionId}`,
      ),
      'sesión de caja',
    );
  }

  openSession(request: OpenCashSessionRequest): Observable<CashSession> {
    return this.require(
      this.http.post<ApiResponse<CashSession>>(
        `${this.config.baseUrl}/cash/sessions/open`,
        request,
      ),
      'sesión de caja',
    );
  }

  closeSession(cashSessionId: string, request: CloseCashSessionRequest): Observable<CashSession> {
    return this.require(
      this.http.post<ApiResponse<CashSession>>(
        `${this.config.baseUrl}/cash/sessions/${cashSessionId}/close`,
        request,
      ),
      'sesión de caja',
    );
  }

  listMovements(cashSessionId: string): Observable<CashMovement[]> {
    return this.http
      .get<ApiResponse<CashMovement[]>>(
        `${this.config.baseUrl}/cash/sessions/${cashSessionId}/movements`,
      )
      .pipe(map((response) => response.data ?? []));
  }

  createMovement(
    cashSessionId: string,
    request: CreateCashMovementRequest,
  ): Observable<CashMovement> {
    return this.require(
      this.http.post<ApiResponse<CashMovement>>(
        `${this.config.baseUrl}/cash/sessions/${cashSessionId}/movements`,
        request,
      ),
      'movimiento de caja',
    );
  }

  private require<T>(request: Observable<ApiResponse<T>>, resource: string): Observable<T> {
    return request.pipe(
      map((response) => {
        if (response.data === null) throw new Error(`La API respondió sin datos de ${resource}.`);
        return response.data;
      }),
    );
  }
}
