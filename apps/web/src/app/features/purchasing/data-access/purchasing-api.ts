import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { InventoryReceipt } from '../../inventory/models/inventory.model';
import {
  CreatePurchaseRequest,
  Purchase,
  PurchaseFilters,
  ReceivePurchaseRequest,
  Supplier,
  SupplierFilters,
  SupplierStatus,
  SupplierUpsertRequest,
} from '../models/purchasing.model';

@Injectable({ providedIn: 'root' })
export class PurchasingApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  listSuppliers(filters: SupplierFilters = {}): Observable<Supplier[]> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    return this.http
      .get<ApiResponse<Supplier[]>>(`${this.apiConfig.baseUrl}/purchasing/suppliers`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  createSupplier(request: SupplierUpsertRequest): Observable<Supplier> {
    return this.requireData(
      this.http.post<ApiResponse<Supplier>>(
        `${this.apiConfig.baseUrl}/purchasing/suppliers`,
        request,
      ),
      'proveedor',
    );
  }

  updateSupplier(supplierId: string, request: SupplierUpsertRequest): Observable<Supplier> {
    return this.requireData(
      this.http.put<ApiResponse<Supplier>>(
        `${this.apiConfig.baseUrl}/purchasing/suppliers/${supplierId}`,
        request,
      ),
      'proveedor',
    );
  }

  changeSupplierStatus(supplierId: string, status: SupplierStatus): Observable<Supplier> {
    return this.requireData(
      this.http.patch<ApiResponse<Supplier>>(
        `${this.apiConfig.baseUrl}/purchasing/suppliers/${supplierId}/status`,
        { status },
      ),
      'proveedor',
    );
  }

  listPurchases(filters: PurchaseFilters = {}): Observable<Purchase[]> {
    let params = new HttpParams();
    if (filters.supplierId) params = params.set('supplierId', filters.supplierId);
    if (filters.warehouseId) params = params.set('warehouseId', filters.warehouseId);
    if (filters.status) params = params.set('status', filters.status);
    return this.http
      .get<ApiResponse<Purchase[]>>(`${this.apiConfig.baseUrl}/purchasing/purchases`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  getPurchase(purchaseId: string): Observable<Purchase> {
    return this.requireData(
      this.http.get<ApiResponse<Purchase>>(
        `${this.apiConfig.baseUrl}/purchasing/purchases/${purchaseId}`,
      ),
      'compra',
    );
  }

  createPurchase(request: CreatePurchaseRequest): Observable<Purchase> {
    return this.requireData(
      this.http.post<ApiResponse<Purchase>>(
        `${this.apiConfig.baseUrl}/purchasing/purchases`,
        request,
      ),
      'compra',
    );
  }

  confirmPurchase(purchaseId: string): Observable<Purchase> {
    return this.requireData(
      this.http.post<ApiResponse<Purchase>>(
        `${this.apiConfig.baseUrl}/purchasing/purchases/${purchaseId}/confirm`,
        {},
      ),
      'compra',
    );
  }

  receivePurchase(
    purchaseId: string,
    request: ReceivePurchaseRequest,
  ): Observable<InventoryReceipt> {
    return this.requireData(
      this.http.post<ApiResponse<InventoryReceipt>>(
        `${this.apiConfig.baseUrl}/purchasing/purchases/${purchaseId}/receive`,
        request,
      ),
      'recepción',
    );
  }

  private requireData<T>(request: Observable<ApiResponse<T>>, resource: string): Observable<T> {
    return request.pipe(
      map((response) => {
        if (response.data === null) throw new Error(`La API respondió sin datos de ${resource}.`);
        return response.data;
      }),
    );
  }
}
