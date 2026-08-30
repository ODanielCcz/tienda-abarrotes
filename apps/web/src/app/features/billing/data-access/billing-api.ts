import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CreateFiscalDocumentRequest,
  FiscalClassificationResult,
  FiscalDocument,
  FiscalProfile,
  FiscalProfileRequest,
  FiscalProfileStatus,
  IssuerProfile,
  IssuerProfileRequest,
} from '../models/billing.model';

@Injectable({ providedIn: 'root' })
export class BillingApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);
  private readonly baseUrl = `${this.config.baseUrl}/billing`;

  listIssuerProfiles(branchId?: string, status?: string): Observable<IssuerProfile[]> {
    let params = new HttpParams();
    if (branchId) params = params.set('branchId', branchId);
    if (status) params = params.set('status', status);
    return this.http
      .get<ApiResponse<IssuerProfile[]>>(`${this.baseUrl}/issuer-profiles`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  createIssuerProfile(request: IssuerProfileRequest): Observable<IssuerProfile> {
    return this.require(
      this.http.post<ApiResponse<IssuerProfile>>(`${this.baseUrl}/issuer-profiles`, request),
      'perfil emisor',
    );
  }

  updateIssuerProfile(id: string, request: IssuerProfileRequest): Observable<IssuerProfile> {
    return this.require(
      this.http.put<ApiResponse<IssuerProfile>>(`${this.baseUrl}/issuer-profiles/${id}`, request),
      'perfil emisor',
    );
  }

  changeIssuerStatus(id: string, status: FiscalProfileStatus): Observable<IssuerProfile> {
    return this.require(
      this.http.patch<ApiResponse<IssuerProfile>>(`${this.baseUrl}/issuer-profiles/${id}/status`, {
        status,
      }),
      'perfil emisor',
    );
  }

  listFiscalProfiles(customerId?: string, status?: string): Observable<FiscalProfile[]> {
    let params = new HttpParams();
    if (customerId) params = params.set('customerId', customerId);
    if (status) params = params.set('status', status);
    return this.http
      .get<ApiResponse<FiscalProfile[]>>(`${this.baseUrl}/fiscal-profiles`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  createFiscalProfile(request: FiscalProfileRequest): Observable<FiscalProfile> {
    return this.require(
      this.http.post<ApiResponse<FiscalProfile>>(`${this.baseUrl}/fiscal-profiles`, request),
      'perfil fiscal',
    );
  }

  updateFiscalProfile(id: string, request: FiscalProfileRequest): Observable<FiscalProfile> {
    return this.require(
      this.http.put<ApiResponse<FiscalProfile>>(`${this.baseUrl}/fiscal-profiles/${id}`, request),
      'perfil fiscal',
    );
  }

  changeFiscalProfileStatus(id: string, status: FiscalProfileStatus): Observable<FiscalProfile> {
    return this.require(
      this.http.patch<ApiResponse<FiscalProfile>>(`${this.baseUrl}/fiscal-profiles/${id}/status`, {
        status,
      }),
      'perfil fiscal',
    );
  }

  listFiscalDocuments(salesOrderId?: string, status?: string): Observable<FiscalDocument[]> {
    let params = new HttpParams();
    if (salesOrderId) params = params.set('salesOrderId', salesOrderId);
    if (status) params = params.set('status', status);
    return this.http
      .get<ApiResponse<FiscalDocument[]>>(`${this.baseUrl}/fiscal-documents`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  getFiscalDocument(id: string): Observable<FiscalDocument> {
    return this.require(
      this.http.get<ApiResponse<FiscalDocument>>(`${this.baseUrl}/fiscal-documents/${id}`),
      'documento fiscal',
    );
  }

  createFiscalDocument(request: CreateFiscalDocumentRequest): Observable<FiscalDocument> {
    return this.require(
      this.http.post<ApiResponse<FiscalDocument>>(`${this.baseUrl}/fiscal-documents`, request),
      'documento fiscal',
    );
  }

  markReady(id: string): Observable<FiscalDocument> {
    return this.require(
      this.http.post<ApiResponse<FiscalDocument>>(
        `${this.baseUrl}/fiscal-documents/${id}/ready`,
        {},
      ),
      'documento fiscal',
    );
  }

  classifyProduct(
    productId: string,
    satProductServiceCode: string,
  ): Observable<FiscalClassificationResult> {
    return this.require(
      this.http.put<ApiResponse<FiscalClassificationResult>>(
        `${this.config.baseUrl}/catalog/products/${productId}/fiscal-classification`,
        { satProductServiceCode },
      ),
      'clasificación fiscal',
    );
  }

  classifyUnit(unitId: string, satUnitCode: string): Observable<FiscalClassificationResult> {
    return this.require(
      this.http.put<ApiResponse<FiscalClassificationResult>>(
        `${this.config.baseUrl}/catalog/units/${unitId}/fiscal-classification`,
        { satUnitCode },
      ),
      'clasificación fiscal',
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
