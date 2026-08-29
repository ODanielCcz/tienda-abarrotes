import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CreateInventoryReceiptRequest,
  InventoryReceipt,
  InventoryLot,
  InventoryPallet,
  LotFilters,
  MovementFilters,
  StockBalance,
  StockMovement,
} from '../models/inventory.model';

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);

  listStock(warehouseId: string): Observable<StockBalance[]> {
    return this.getList('/inventory/stock', new HttpParams().set('warehouseId', warehouseId));
  }

  listLots(filters: LotFilters): Observable<InventoryLot[]> {
    let params = new HttpParams().set('warehouseId', filters.warehouseId);
    if (filters.productPresentationId) {
      params = params.set('productPresentationId', filters.productPresentationId);
    }
    if (filters.status) params = params.set('status', filters.status);
    if (filters.expiresBefore) params = params.set('expiresBefore', filters.expiresBefore);
    if (filters.expiresAfter) params = params.set('expiresAfter', filters.expiresAfter);
    return this.getList('/inventory/lots', params);
  }

  listPallets(warehouseId: string, status?: string): Observable<InventoryPallet[]> {
    let params = new HttpParams().set('warehouseId', warehouseId);
    if (status) params = params.set('status', status);
    return this.getList('/inventory/pallets', params);
  }

  listMovements(filters: MovementFilters): Observable<StockMovement[]> {
    let params = new HttpParams().set('warehouseId', filters.warehouseId);
    if (filters.movementType) params = params.set('movementType', filters.movementType);
    if (filters.status) params = params.set('status', filters.status);
    return this.getList('/inventory/movements', params);
  }

  createReceipt(request: CreateInventoryReceiptRequest): Observable<InventoryReceipt> {
    return this.http
      .post<ApiResponse<InventoryReceipt>>(`${this.apiConfig.baseUrl}/inventory/receipts`, request)
      .pipe(
        map((response) => {
          if (response.data === null) {
            throw new Error('La API respondió sin datos de la recepción.');
          }
          return response.data;
        }),
      );
  }

  private getList<T>(path: string, params: HttpParams): Observable<T[]> {
    return this.http
      .get<ApiResponse<T[]>>(`${this.apiConfig.baseUrl}${path}`, { params })
      .pipe(map((response) => response.data ?? []));
  }
}
