import { HttpErrorResponse } from '@angular/common/http';

import { apiErrorMessage } from './api-error-message';

describe('apiErrorMessage', () => {
  it('returns the backend message with its correlation id', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        message: 'El producto ya existe',
        correlationId: 'correlation-409',
      },
    });

    expect(apiErrorMessage(error, 'No fue posible completar la operación.')).toBe(
      'El producto ya existe (Referencia: correlation-409)',
    );
  });

  it('returns the backend message when no correlation id is available', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        message: 'La solicitud es inválida',
      },
    });

    expect(apiErrorMessage(error, 'Error de consulta.')).toBe('La solicitud es inválida');
  });

  it('returns the fallback for an unknown error shape', () => {
    expect(apiErrorMessage(new Error('network'), 'Error de consulta.')).toBe(
      'Error de consulta.',
    );
  });
});
