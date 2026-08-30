import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  ActiveStatus,
  Branch,
  BranchRequest,
  CashRegister,
  CashRegisterRequest,
  CashRegisterStatus,
  Device,
  DeviceRequest,
  DeviceStatus,
  Warehouse,
  WarehouseRequest,
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

  getBranch(branchId: string): Observable<Branch> {
    return this.require(
      this.http.get<ApiResponse<Branch>>(
        `${this.apiConfig.baseUrl}/organization/branches/${branchId}`,
      ),
      'sucursal',
    );
  }

  createBranch(request: BranchRequest): Observable<Branch> {
    return this.require(
      this.http.post<ApiResponse<Branch>>(
        `${this.apiConfig.baseUrl}/organization/branches`,
        request,
      ),
      'sucursal',
    );
  }

  updateBranch(branchId: string, request: BranchRequest): Observable<Branch> {
    return this.require(
      this.http.put<ApiResponse<Branch>>(
        `${this.apiConfig.baseUrl}/organization/branches/${branchId}`,
        request,
      ),
      'sucursal',
    );
  }

  changeBranchStatus(branchId: string, status: ActiveStatus): Observable<Branch> {
    return this.require(
      this.http.patch<ApiResponse<Branch>>(
        `${this.apiConfig.baseUrl}/organization/branches/${branchId}/status`,
        { status },
      ),
      'sucursal',
    );
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

  getWarehouse(warehouseId: string): Observable<Warehouse> {
    return this.require(
      this.http.get<ApiResponse<Warehouse>>(
        `${this.apiConfig.baseUrl}/organization/warehouses/${warehouseId}`,
      ),
      'almacén',
    );
  }

  createWarehouse(request: WarehouseRequest): Observable<Warehouse> {
    return this.require(
      this.http.post<ApiResponse<Warehouse>>(
        `${this.apiConfig.baseUrl}/organization/warehouses`,
        request,
      ),
      'almacén',
    );
  }

  updateWarehouse(warehouseId: string, request: WarehouseRequest): Observable<Warehouse> {
    return this.require(
      this.http.put<ApiResponse<Warehouse>>(
        `${this.apiConfig.baseUrl}/organization/warehouses/${warehouseId}`,
        request,
      ),
      'almacén',
    );
  }

  changeWarehouseStatus(warehouseId: string, status: ActiveStatus): Observable<Warehouse> {
    return this.require(
      this.http.patch<ApiResponse<Warehouse>>(
        `${this.apiConfig.baseUrl}/organization/warehouses/${warehouseId}/status`,
        { status },
      ),
      'almacén',
    );
  }

  listCashRegisters(branchId?: string, status?: CashRegisterStatus): Observable<CashRegister[]> {
    let params = new HttpParams();
    if (branchId !== undefined) params = params.set('branchId', branchId);
    if (status !== undefined) params = params.set('status', status);
    return this.http
      .get<ApiResponse<CashRegister[]>>(`${this.apiConfig.baseUrl}/organization/cash-registers`, {
        params,
      })
      .pipe(map((response) => response.data ?? []));
  }

  getCashRegister(cashRegisterId: string): Observable<CashRegister> {
    return this.require(
      this.http.get<ApiResponse<CashRegister>>(
        `${this.apiConfig.baseUrl}/organization/cash-registers/${cashRegisterId}`,
      ),
      'caja registradora',
    );
  }

  createCashRegister(request: CashRegisterRequest): Observable<CashRegister> {
    return this.require(
      this.http.post<ApiResponse<CashRegister>>(
        `${this.apiConfig.baseUrl}/organization/cash-registers`,
        request,
      ),
      'caja registradora',
    );
  }

  updateCashRegister(
    cashRegisterId: string,
    request: CashRegisterRequest,
  ): Observable<CashRegister> {
    return this.require(
      this.http.put<ApiResponse<CashRegister>>(
        `${this.apiConfig.baseUrl}/organization/cash-registers/${cashRegisterId}`,
        request,
      ),
      'caja registradora',
    );
  }

  changeCashRegisterStatus(
    cashRegisterId: string,
    status: CashRegisterStatus,
  ): Observable<CashRegister> {
    return this.require(
      this.http.patch<ApiResponse<CashRegister>>(
        `${this.apiConfig.baseUrl}/organization/cash-registers/${cashRegisterId}/status`,
        { status },
      ),
      'caja registradora',
    );
  }

  listDevices(branchId?: string, status?: DeviceStatus): Observable<Device[]> {
    let params = new HttpParams();
    if (branchId !== undefined) params = params.set('branchId', branchId);
    if (status !== undefined) params = params.set('status', status);
    return this.http
      .get<ApiResponse<Device[]>>(`${this.apiConfig.baseUrl}/organization/devices`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  getDevice(deviceId: string): Observable<Device> {
    return this.require(
      this.http.get<ApiResponse<Device>>(
        `${this.apiConfig.baseUrl}/organization/devices/${deviceId}`,
      ),
      'dispositivo',
    );
  }

  createDevice(request: DeviceRequest): Observable<Device> {
    return this.require(
      this.http.post<ApiResponse<Device>>(
        `${this.apiConfig.baseUrl}/organization/devices`,
        request,
      ),
      'dispositivo',
    );
  }

  updateDevice(deviceId: string, request: DeviceRequest): Observable<Device> {
    return this.require(
      this.http.put<ApiResponse<Device>>(
        `${this.apiConfig.baseUrl}/organization/devices/${deviceId}`,
        request,
      ),
      'dispositivo',
    );
  }

  changeDeviceStatus(deviceId: string, status: DeviceStatus): Observable<Device> {
    return this.require(
      this.http.patch<ApiResponse<Device>>(
        `${this.apiConfig.baseUrl}/organization/devices/${deviceId}/status`,
        { status },
      ),
      'dispositivo',
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
