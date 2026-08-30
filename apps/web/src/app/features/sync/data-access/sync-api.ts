import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  DeviceCheckpoint,
  OutboxBatch,
  ResolveConflictRequest,
  SyncConflict,
  SyncOperation,
  SyncOperationRequest,
} from '../models/sync.model';

@Injectable({ providedIn: 'root' })
export class SyncApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);
  private readonly baseUrl = `${this.config.baseUrl}/sync`;

  ingest(request: SyncOperationRequest): Observable<SyncOperation> {
    return this.require(
      this.http.post<ApiResponse<SyncOperation>>(`${this.baseUrl}/inbox`, request),
      'operación Sync',
    );
  }

  getOutbox(deviceId: string, afterSequence?: number, limit = 100): Observable<OutboxBatch> {
    let params = new HttpParams().set('deviceId', deviceId).set('limit', limit);
    if (afterSequence !== undefined) params = params.set('afterSequence', afterSequence);
    return this.require(
      this.http.get<ApiResponse<OutboxBatch>>(`${this.baseUrl}/outbox`, { params }),
      'outbox',
    );
  }

  getCheckpoint(deviceId: string): Observable<DeviceCheckpoint> {
    return this.require(
      this.http.get<ApiResponse<DeviceCheckpoint>>(
        `${this.baseUrl}/devices/${deviceId}/checkpoint`,
      ),
      'checkpoint',
    );
  }

  acknowledge(deviceId: string, outboxSequence: number): Observable<DeviceCheckpoint> {
    return this.require(
      this.http.post<ApiResponse<DeviceCheckpoint>>(
        `${this.baseUrl}/devices/${deviceId}/checkpoint`,
        { outboxSequence },
      ),
      'checkpoint',
    );
  }

  listConflicts(deviceId: string, resolved?: boolean): Observable<SyncConflict[]> {
    let params = new HttpParams().set('deviceId', deviceId);
    if (resolved !== undefined) params = params.set('resolved', resolved);
    return this.http
      .get<ApiResponse<SyncConflict[]>>(`${this.baseUrl}/conflicts`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  resolveConflict(conflictId: string, request: ResolveConflictRequest): Observable<SyncConflict> {
    return this.require(
      this.http.post<ApiResponse<SyncConflict>>(
        `${this.baseUrl}/conflicts/${conflictId}/resolve`,
        request,
      ),
      'conflicto Sync',
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
