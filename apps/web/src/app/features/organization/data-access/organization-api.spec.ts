import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { OrganizationApi } from './organization-api';

describe('OrganizationApi', () => {
  let api: OrganizationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(OrganizationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads active branch-scoped operational resources', () => {
    api.listBranches('ACTIVE').subscribe();
    const branches = http.expectOne(
      (request) =>
        request.url === '/api/v1/organization/branches' &&
        request.params.get('status') === 'ACTIVE',
    );
    expect(branches.request.method).toBe('GET');
    branches.flush(response([]));

    api.listWarehouses('branch-a', 'ACTIVE').subscribe();
    const warehouses = http.expectOne(
      (request) =>
        request.url === '/api/v1/organization/warehouses' &&
        request.params.get('branchId') === 'branch-a' &&
        request.params.get('status') === 'ACTIVE',
    );
    warehouses.flush(response([]));

    api.listCashRegisters('branch-a', 'ACTIVE').subscribe();
    const registers = http.expectOne(
      (request) =>
        request.url === '/api/v1/organization/cash-registers' &&
        request.params.get('branchId') === 'branch-a' &&
        request.params.get('status') === 'ACTIVE',
    );
    registers.flush(response([]));

    api.listDevices('branch-a', 'ACTIVE').subscribe();
    const devices = http.expectOne(
      (request) =>
        request.url === '/api/v1/organization/devices' &&
        request.params.get('branchId') === 'branch-a' &&
        request.params.get('status') === 'ACTIVE',
    );
    devices.flush(response([]));
  });

  it('creates, updates and changes branch status', () => {
    const request = {
      code: 'NORTE',
      name: 'Sucursal norte',
      legalName: 'Tienda Norte SA',
      timezone: 'America/Mexico_City',
      currencyCode: 'MXN',
    };
    api.createBranch(request).subscribe();
    expect(http.expectOne('/api/v1/organization/branches').request.method).toBe('POST');

    api.updateBranch('branch-1', request).subscribe();
    expect(http.expectOne('/api/v1/organization/branches/branch-1').request.method).toBe('PUT');

    api.changeBranchStatus('branch-1', 'INACTIVE').subscribe();
    const status = http.expectOne('/api/v1/organization/branches/branch-1/status');
    expect(status.request.body).toEqual({ status: 'INACTIVE' });

    http.match(() => true).forEach((pending) => pending.flush(response({})));
  });

  it('manages warehouses, registers and devices', () => {
    api
      .createWarehouse({
        branchId: 'branch-1',
        code: 'MAIN',
        name: 'Principal',
        warehouseType: 'STORE',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/organization/warehouses').request.method).toBe('POST');

    api
      .createCashRegister({
        branchId: 'branch-1',
        deviceId: null,
        code: 'POS-01',
        name: 'Caja 1',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/organization/cash-registers').request.method).toBe('POST');

    api
      .createDevice({
        branchId: 'branch-1',
        warehouseId: 'warehouse-1',
        deviceCode: 'WEB-01',
        deviceType: 'WEB',
        platform: 'WEB',
        appVersion: '1.0.0',
      })
      .subscribe();
    expect(http.expectOne('/api/v1/organization/devices').request.method).toBe('POST');

    http.match(() => true).forEach((pending) => pending.flush(response({})));
  });

  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'FOUND',
      reason: 'OK',
      message: 'OK',
      data,
      errors: null,
      path: '',
      correlationId: 'organization-test',
    };
  }
});
