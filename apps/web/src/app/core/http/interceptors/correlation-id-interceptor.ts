import { HttpInterceptorFn } from '@angular/common/http';

export const correlationIdInterceptor: HttpInterceptorFn = (request, next) => {
  const correlationId = request.headers.get('X-Correlation-ID') ?? crypto.randomUUID();

  return next(
    request.clone({
      setHeaders: {
        'X-Correlation-ID': correlationId,
      },
    }),
  );
};
