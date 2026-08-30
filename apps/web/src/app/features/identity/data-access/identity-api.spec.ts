import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { IdentityApi } from './identity-api';

describe('IdentityApi', () => {
  let api: IdentityApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(IdentityApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists users with filters and unwraps the standard response', () => {
    api.listUsers({ search: 'caja', status: 'ACTIVE', roleCode: 'CASHIER' }).subscribe();

    http
      .expectOne('/api/v1/identity/users?search=caja&status=ACTIVE&roleCode=CASHIER')
      .flush(response([]));
  });

  it('creates and updates users through the management endpoints', () => {
    api
      .createUser({
        username: 'cajero',
        displayName: 'Cajero',
        password: 'Temporal123!',
        roleCodes: ['CASHIER'],
      })
      .subscribe();
    const create = http.expectOne('/api/v1/identity/users');
    expect(create.request.method).toBe('POST');
    create.flush(response(user()));

    api.updateUser('user-1', { username: 'cajero', displayName: 'Cajero principal' }).subscribe();
    const update = http.expectOne('/api/v1/identity/users/user-1');
    expect(update.request.method).toBe('PUT');
    update.flush(response(user()));

    api.changeUserStatus('user-1', 'DISABLED').subscribe();
    const status = http.expectOne('/api/v1/identity/users/user-1/status');
    expect(status.request.body).toEqual({ status: 'DISABLED' });
    status.flush(response(user()));

    api.changePassword('user-1', 'Nueva123!').subscribe();
    const password = http.expectOne('/api/v1/identity/users/user-1/password');
    expect(password.request.body).toEqual({ password: 'Nueva123!' });
    password.flush(response(user()));
  });

  it('replaces user roles and branches', () => {
    api.assignUserRoles('user-1', ['CASHIER']).subscribe();
    const roles = http.expectOne('/api/v1/identity/users/user-1/roles');
    expect(roles.request.method).toBe('PUT');
    expect(roles.request.body).toEqual({ roleCodes: ['CASHIER'] });
    roles.flush(response(user()));

    api.assignUserBranches('user-1', ['branch-1']).subscribe();
    const branches = http.expectOne('/api/v1/identity/users/user-1/branches');
    expect(branches.request.body).toEqual({ branchIds: ['branch-1'] });
    branches.flush(response(user()));
  });

  it('manages roles and their permissions', () => {
    api.listRoles().subscribe();
    http.expectOne('/api/v1/identity/roles').flush(response([]));

    api.getRole('role-1').subscribe();
    http.expectOne('/api/v1/identity/roles/role-1').flush(response(role()));

    api.createRole({ code: 'CASHIER', name: 'Cajero', description: null }).subscribe();
    const create = http.expectOne('/api/v1/identity/roles');
    expect(create.request.method).toBe('POST');
    create.flush(response(role()));

    api.updateRole('role-1', { code: 'CASHIER', name: 'Caja', description: null }).subscribe();
    const update = http.expectOne('/api/v1/identity/roles/role-1');
    expect(update.request.method).toBe('PUT');
    update.flush(response(role()));

    api.changeRoleStatus('role-1', 'INACTIVE').subscribe();
    const status = http.expectOne('/api/v1/identity/roles/role-1/status');
    expect(status.request.body).toEqual({ status: 'INACTIVE' });
    status.flush(response(role()));

    api.assignRolePermissions('role-1', ['SALES_ORDER_READ']).subscribe();
    const permissions = http.expectOne('/api/v1/identity/roles/role-1/permissions');
    expect(permissions.request.body).toEqual({ permissionCodes: ['SALES_ORDER_READ'] });
    permissions.flush(response(role()));

    api.listPermissions().subscribe();
    http.expectOne('/api/v1/identity/permissions').flush(response([]));
  });

  function response<T>(data: T): ApiResponse<T> {
    return {
      timestamp: '2026-08-29T00:00:00Z',
      status: 200,
      code: 'OK',
      reason: 'OK',
      message: 'ok',
      data,
      errors: null,
      path: '/api/v1/identity',
      correlationId: 'identity-test',
    };
  }

  function user() {
    return {
      userId: 'user-1',
      username: 'cajero',
      displayName: 'Cajero',
      status: 'ACTIVE' as const,
      roles: ['CASHIER'],
      permissions: [],
      branchIds: ['branch-1'],
      createdAt: '2026-08-29T00:00:00Z',
      updatedAt: '2026-08-29T00:00:00Z',
    };
  }

  function role() {
    return {
      roleId: 'role-1',
      code: 'CASHIER',
      name: 'Cajero',
      description: null,
      system: false,
      status: 'ACTIVE' as const,
      permissions: [],
      createdAt: '2026-08-29T00:00:00Z',
    };
  }
});
