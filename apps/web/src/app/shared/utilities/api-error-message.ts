import { HttpErrorResponse } from '@angular/common/http';

interface ApiErrorPayload {
  message?: unknown;
  correlationId?: unknown;
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse) || !isApiErrorPayload(error.error)) {
    return fallback;
  }

  const message = typeof error.error.message === 'string' ? error.error.message : fallback;
  const correlationId =
    typeof error.error.correlationId === 'string' && error.error.correlationId.trim().length > 0
      ? error.error.correlationId
      : null;

  return correlationId === null ? message : `${message} (Referencia: ${correlationId})`;
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  return typeof value === 'object' && value !== null;
}
