import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_CONFIG } from '../../../core/config/api.config';
import { CustomerApi } from './customer-api';

describe('CustomerApi', () => {
  let api: CustomerApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(CustomerApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('lists and creates customers', () => {
    api.list({ search: 'ana', customerType: 'PERSON', status: 'ACTIVE' }).subscribe();
    const list = http.expectOne(
      (r) =>
        r.url === '/api/v1/sales/customers' &&
        r.params.get('search') === 'ana' &&
        r.params.get('customerType') === 'PERSON',
    );
    expect(list.request.method).toBe('GET');
    list.flush(response([]));
    const payload = {
      customerCode: 'CLI-1',
      customerType: 'PERSON' as const,
      displayName: 'Ana López',
      email: 'ana@example.com',
      phone: '5555',
    };
    api.create(payload).subscribe();
    const create = http.expectOne('/api/v1/sales/customers');
    expect(create.request.body).toEqual(payload);
    create.flush(response(customer));
  });
  const customer = {
    customerId: 'customer-1',
    customerCode: 'CLI-1',
    customerType: 'PERSON',
    displayName: 'Ana López',
    email: 'ana@example.com',
    phone: '5555',
    status: 'ACTIVE',
    createdAt: '',
    updatedAt: '',
  };
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
      correlationId: 'customer-test',
    };
  }
});
