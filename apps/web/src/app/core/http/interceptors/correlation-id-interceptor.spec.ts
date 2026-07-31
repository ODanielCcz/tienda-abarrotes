import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { correlationIdInterceptor } from './correlation-id-interceptor';

describe('correlationIdInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should generate a correlation id when the request does not have one', () => {
    http.get('/api/v1/test').subscribe();

    const request = httpTesting.expectOne('/api/v1/test');
    const correlationId = request.request.headers.get('X-Correlation-ID');

    expect(correlationId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );

    request.flush({});
  });

  it('should preserve a correlation id provided by the client', () => {
    http
      .get('/api/v1/test', {
        headers: {
          'X-Correlation-ID': 'prueba-web-123',
        },
      })
      .subscribe();

    const request = httpTesting.expectOne('/api/v1/test');

    expect(request.request.headers.get('X-Correlation-ID')).toBe('prueba-web-123');

    request.flush({});
  });
});
