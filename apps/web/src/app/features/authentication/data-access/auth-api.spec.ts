import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';
import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { LoginResponse } from '../models/login-response';
import { AuthApi } from './auth-api';

describe('AuthApi', () => {
  let authApi: AuthApi;
  let sessionStore: AuthSessionStore;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: API_CONFIG,
          useValue: {
            baseUrl: '/api/v1',
          },
        },
      ],
    });

    authApi = TestBed.inject(AuthApi);
    sessionStore = TestBed.inject(AuthSessionStore);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    sessionStorage.clear();
  });

  it('should authenticate and save the returned session', () => {
    const loginResponse: LoginResponse = {
      accessToken: 'jwt-token',
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    };

    const apiResponse: ApiResponse<LoginResponse> = {
      timestamp: new Date().toISOString(),
      status: 200,
      code: 'LOGIN_SUCCEEDED',
      reason: 'OK',
      message: 'Inicio de sesión correcto',
      data: loginResponse,
      errors: null,
      path: '/api/v1/auth/login',
      correlationId: 'test-correlation-id',
    };

    authApi
      .login({
        username: 'admin',
        password: 'password',
      })
      .subscribe((result) => {
        expect(result).toEqual(loginResponse);
        expect(sessionStore.accessToken()).toBe('jwt-token');
      });

    const request = httpTesting.expectOne('/api/v1/auth/login');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'admin',
      password: 'password',
    });

    request.flush(apiResponse);
  });
});
