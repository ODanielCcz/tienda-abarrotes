import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { ReportApi } from './report-api';

describe('ReportApi', () => {
  let api: ReportApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(ReportApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads sales summary and period data with typed filters', () => {
    const filters = {
      from: '2026-08-01',
      to: '2026-08-29',
      branchId: 'branch-1',
      warehouseId: 'warehouse-1',
      customerId: 'customer-1',
    };
    api.salesSummary(filters).subscribe();
    const summary = http.expectOne((request) =>
      hasFilters(request, '/api/v1/reports/sales-summary'),
    );
    expect(summary.request.method).toBe('GET');
    summary.flush(response({ ticketCount: 1 }));

    api.salesByPeriod({ ...filters, groupBy: 'DAY' }).subscribe();
    const period = http.expectOne(
      (request) =>
        hasFilters(request, '/api/v1/reports/sales-by-period') &&
        request.params.get('groupBy') === 'DAY',
    );
    expect(period.request.method).toBe('GET');
    period.flush(response([]));
  });

  it('loads profitability, stock, expiration, returns and cash reports', () => {
    api.grossMargin({ branchId: 'branch-1' }).subscribe();
    http.expectOne('/api/v1/reports/gross-margin?branchId=branch-1').flush(response({}));

    api.productProfitability({ warehouseId: 'warehouse-1', limit: 20 }).subscribe();
    http
      .expectOne('/api/v1/reports/product-profitability?warehouseId=warehouse-1&limit=20')
      .flush(response([]));

    api.stockValuation({ warehouseId: 'warehouse-1', limit: 20 }).subscribe();
    http
      .expectOne('/api/v1/reports/stock-valuation?warehouseId=warehouse-1&limit=20')
      .flush(response({ items: [] }));

    api.expiringProducts({ warehouseId: 'warehouse-1', days: 30, limit: 20 }).subscribe();
    http
      .expectOne('/api/v1/reports/expiring-products?warehouseId=warehouse-1&limit=20&days=30')
      .flush(response([]));

    api.returnsSummary({ from: '2026-08-01', to: '2026-08-29', groupBy: 'DAY' }).subscribe();
    http
      .expectOne('/api/v1/reports/returns-summary?from=2026-08-01&to=2026-08-29&groupBy=DAY')
      .flush(response({ periods: [] }));

    api.cashSummary({ branchId: 'branch-1', limit: 20 }).subscribe();
    http.expectOne('/api/v1/reports/cash-summary?branchId=branch-1&limit=20').flush(response([]));
  });

  function hasFilters(request: import('@angular/common/http').HttpRequest<unknown>, url: string) {
    return (
      request.url === url &&
      request.params.get('from') === '2026-08-01' &&
      request.params.get('to') === '2026-08-29' &&
      request.params.get('branchId') === 'branch-1' &&
      request.params.get('warehouseId') === 'warehouse-1' &&
      request.params.get('customerId') === 'customer-1'
    );
  }

  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'OK',
      reason: 'OK',
      message: 'OK',
      data,
      errors: null,
      path: '',
      correlationId: 'report-test',
    };
  }
});
