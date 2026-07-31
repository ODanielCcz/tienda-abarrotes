import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { API_CONFIG, apiConfig } from './core/config/api.config';
import { authTokenInterceptor } from './core/http/interceptors/auth-token-interceptor';
import { correlationIdInterceptor } from './core/http/interceptors/correlation-id-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([correlationIdInterceptor, authTokenInterceptor])),
    {
      provide: API_CONFIG,
      useValue: apiConfig,
    },
  ],
};
