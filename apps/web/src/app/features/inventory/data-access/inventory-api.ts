import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CreateInventoryReceiptRequest,
  InventoryAdjustmentRequest,
  InventoryCount,
  InventoryCountRequest,
  InventoryReceipt,
  InventoryReservation,
  InventoryTransferRequest,
  InventoryLot,
  InventoryPallet,
  LotFilters,
  MovementFilters,
  StockBalance,
  StockMovement,
  ReservationRequest,
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

  adjust(request: InventoryAdjustmentRequest): Observable<StockMovement> {
    return this.postWithData('/inventory/adjustments', request, 'ajuste');
  }

  transfer(request: InventoryTransferRequest): Observable<StockMovement[]> {
    return this.postWithData('/inventory/transfers', request, 'traspaso');
  }

  createCount(request: InventoryCountRequest): Observable<InventoryCount> {
    return this.postWithData('/inventory/counts', request, 'conteo');
  }

  confirmCount(inventoryCountId: string): Observable<InventoryCount> {
    return this.postWithData(`/inventory/counts/${inventoryCountId}/confirm`, {}, 'conteo');
  }

  reserve(request: ReservationRequest): Observable<InventoryReservation> {
    return this.postWithData('/inventory/reservations', request, 'reserva');
  }

  releaseReservation(reservationId: string): Observable<InventoryReservation> {
    return this.postWithData(`/inventory/reservations/${reservationId}/release`, {}, 'reserva');
  }

  listExpiringLots(expiresBefore?: string): Observable<InventoryLot[]> {
    let params = new HttpParams();
    if (expiresBefore) params = params.set('expiresBefore', expiresBefore);
    return this.getList('/inventory/expiring-lots', params);
  }

  private getList<T>(path: string, params: HttpParams): Observable<T[]> {
    return this.http
      .get<ApiResponse<T[]>>(`${this.apiConfig.baseUrl}${path}`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  private postWithData<T>(path: string, body: unknown, resource: string): Observable<T> {
    return this.http.post<ApiResponse<T>>(`${this.apiConfig.baseUrl}${path}`, body).pipe(
      map((response) => {
        if (response.data === null) {
          throw new Error(`La API respondió sin datos de ${resource}.`);
        }
        return response.data;
      }),
    );
  }
}
