import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  CreateUserRequest,
  ManagedUser,
  PermissionSummary,
  RoleDetail,
  RoleRequest,
  RoleStatus,
  RoleSummary,
  UpdateUserRequest,
  UserAccountStatus,
  UserFilters,
} from '../models/identity.model';

@Injectable({ providedIn: 'root' })
export class IdentityApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(API_CONFIG);
  private readonly baseUrl = `${this.config.baseUrl}/identity`;

  listUsers(filters: UserFilters = {}): Observable<ManagedUser[]> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.roleCode) params = params.set('roleCode', filters.roleCode);
    return this.http
      .get<ApiResponse<ManagedUser[]>>(`${this.baseUrl}/users`, { params })
      .pipe(map((response) => response.data ?? []));
  }

  getUser(userId: string): Observable<ManagedUser> {
    return this.require(
      this.http.get<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}`),
      'usuario',
    );
  }

  createUser(request: CreateUserRequest): Observable<ManagedUser> {
    return this.require(
      this.http.post<ApiResponse<ManagedUser>>(`${this.baseUrl}/users`, request),
      'usuario',
    );
  }

  updateUser(userId: string, request: UpdateUserRequest): Observable<ManagedUser> {
    return this.require(
      this.http.put<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}`, request),
      'usuario',
    );
  }

  changeUserStatus(userId: string, status: UserAccountStatus): Observable<ManagedUser> {
    return this.require(
      this.http.patch<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}/status`, {
        status,
      }),
      'usuario',
    );
  }

  changePassword(userId: string, password: string): Observable<ManagedUser> {
    return this.require(
      this.http.post<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}/password`, {
        password,
      }),
      'usuario',
    );
  }

  assignUserRoles(userId: string, roleCodes: string[]): Observable<ManagedUser> {
    return this.require(
      this.http.put<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}/roles`, {
        roleCodes,
      }),
      'usuario',
    );
  }

  assignUserBranches(userId: string, branchIds: string[]): Observable<ManagedUser> {
    return this.require(
      this.http.put<ApiResponse<ManagedUser>>(`${this.baseUrl}/users/${userId}/branches`, {
        branchIds,
      }),
      'usuario',
    );
  }

  listRoles(): Observable<RoleSummary[]> {
    return this.http
      .get<ApiResponse<RoleSummary[]>>(`${this.baseUrl}/roles`)
      .pipe(map((response) => response.data ?? []));
  }

  getRole(roleId: string): Observable<RoleDetail> {
    return this.require(
      this.http.get<ApiResponse<RoleDetail>>(`${this.baseUrl}/roles/${roleId}`),
      'rol',
    );
  }

  createRole(request: RoleRequest): Observable<RoleDetail> {
    return this.require(
      this.http.post<ApiResponse<RoleDetail>>(`${this.baseUrl}/roles`, request),
      'rol',
    );
  }

  updateRole(roleId: string, request: RoleRequest): Observable<RoleDetail> {
    return this.require(
      this.http.put<ApiResponse<RoleDetail>>(`${this.baseUrl}/roles/${roleId}`, request),
      'rol',
    );
  }

  changeRoleStatus(roleId: string, status: RoleStatus): Observable<RoleDetail> {
    return this.require(
      this.http.patch<ApiResponse<RoleDetail>>(`${this.baseUrl}/roles/${roleId}/status`, {
        status,
      }),
      'rol',
    );
  }

  assignRolePermissions(roleId: string, permissionCodes: string[]): Observable<RoleDetail> {
    return this.require(
      this.http.put<ApiResponse<RoleDetail>>(`${this.baseUrl}/roles/${roleId}/permissions`, {
        permissionCodes,
      }),
      'rol',
    );
  }

  listPermissions(): Observable<PermissionSummary[]> {
    return this.http
      .get<ApiResponse<PermissionSummary[]>>(`${this.baseUrl}/permissions`)
      .pipe(map((response) => response.data ?? []));
  }

  private require<T>(request: Observable<ApiResponse<T>>, resource: string): Observable<T> {
    return request.pipe(
      map((response) => {
        if (response.data === null) {
          throw new Error(`La API respondió sin datos del ${resource}.`);
        }
        return response.data;
      }),
    );
  }
}
