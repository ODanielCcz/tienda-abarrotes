import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../auth/auth-session-store';
import { API_CONFIG } from '../../config/api.config';
import { authTokenInterceptor } from './auth-token-interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let sessionStore: AuthSessionStore;

  const routerMock = {
    url: '/dashboard',
    navigate: vi.fn().mockResolvedValue(true),
  };

  beforeEach(() => {
    sessionStorage.clear();
    routerMock.navigate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        {
          provide: API_CONFIG,
          useValue: {
            baseUrl: '/api/v1',
          },
        },
        {
          provide: Router,
          useValue: routerMock,
        },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(AuthSessionStore);
  });

  afterEach(() => {
    httpTesting.verify();
    sessionStorage.clear();
  });

  it('should add the bearer token to API requests', () => {
    sessionStore.save({
      accessToken: 'jwt-de-prueba',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    http.get('/api/v1/catalog/brands').subscribe();

    const request = httpTesting.expectOne('/api/v1/catalog/brands');

    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');

    request.flush({});
  });

  it('should not add authorization to the login request', () => {
    sessionStore.save({
      accessToken: 'jwt-de-prueba',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    http.post('/api/v1/auth/login', {}).subscribe();

    const request = httpTesting.expectOne('/api/v1/auth/login');

    expect(request.request.headers.has('Authorization')).toBe(false);

    request.flush({});
  });

  it('should clear the session and redirect after a 401 response', () => {
    sessionStore.save({
      accessToken: 'jwt-expirado',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    http.get('/api/v1/catalog/brands').subscribe({
      error: () => undefined,
    });

    const request = httpTesting.expectOne('/api/v1/catalog/brands');

    request.flush(
      {
        message: 'Token inválido',
      },
      {
        status: 401,
        statusText: 'Unauthorized',
      },
    );

    expect(sessionStore.isAuthenticated()).toBe(false);
    expect(routerMock.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {
        returnUrl: '/dashboard',
      },
    });
  });
});
