import { HttpResponse, HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthSessionStore } from '../../auth/auth-session-store';
import { API_CONFIG } from '../../config/api.config';

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const sessionStore = inject(AuthSessionStore);
  const apiConfig = inject(API_CONFIG);
  const router = inject(Router);

  const belongsToApi = req.url.startsWith(apiConfig.baseUrl);
  const isLoginRequest = req.url === `${apiConfig.baseUrl}/auth/login`;

  if (!belongsToApi || isLoginRequest) {
    return next(req);
  }

  const accessToken = sessionStore.accessToken();
  const tokenType = sessionStore.session()?.tokenType ?? `Bearer`;

  const authorizedRequest = accessToken
    ? req.clone({
        setHeaders: {
          Authorization: `${tokenType} ${accessToken}`,
        },
      })
    : req;

  return next(authorizedRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        const returnUrl = router.url !== '/login' ? router.url : '/dashboard';

        sessionStore.clear();

        void router.navigate(['/login'], {
          queryParams: {
            returnUrl,
          },
        });
      }

      return throwError(() => error);
    }),
  );
};
