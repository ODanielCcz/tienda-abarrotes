import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { AuthSessionStore } from '../../../core/auth/auth-session-store';
import { API_CONFIG } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { LoginRequest } from '../models/login-request';
import { LoginResponse } from '../models/login-response';

@Injectable({
  providedIn: 'root',
})
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly apiConfig = inject(API_CONFIG);
  private readonly sessionStore = inject(AuthSessionStore);

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<ApiResponse<LoginResponse>>(`${this.apiConfig.baseUrl}/auth/login`, credentials)
      .pipe(
        map((response) => {
          if (response.data === null) {
            throw new Error(
              `El backend no devolvió la sesión. Correlación: ${response.correlationId}`,
            );
          }

          this.sessionStore.save(response.data);

          return response.data;
        }),
      );
  }

  logout(): void {
    this.sessionStore.clear();
  }
}
