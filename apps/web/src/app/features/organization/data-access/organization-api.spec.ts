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
      (request) => request.url === '/api/v1/organization/branches' && request.params.get('status') === 'ACTIVE',
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
