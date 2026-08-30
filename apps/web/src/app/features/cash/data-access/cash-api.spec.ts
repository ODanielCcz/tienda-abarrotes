import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { CashApi } from './cash-api';

describe('CashApi', () => {
  let api: CashApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(CashApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists and opens cash sessions', () => {
    api.listSessions({ cashRegisterId: 'register-1', status: 'OPEN' }).subscribe();
    const list = http.expectOne(
      (request) =>
        request.url === '/api/v1/cash/sessions' &&
        request.params.get('cashRegisterId') === 'register-1' &&
        request.params.get('status') === 'OPEN',
    );
    expect(list.request.method).toBe('GET');
    list.flush(response([]));

    const payload = { cashRegisterId: 'register-1', openingAmount: 1000, notes: 'Turno' };
    api.openSession(payload).subscribe();
    const open = http.expectOne('/api/v1/cash/sessions/open');
    expect(open.request.method).toBe('POST');
    expect(open.request.body).toEqual(payload);
    open.flush(response(session));
  });

  it('loads movements, creates a manual movement and closes the session', () => {
    api.listMovements('session-1').subscribe();
    const movements = http.expectOne('/api/v1/cash/sessions/session-1/movements');
    expect(movements.request.method).toBe('GET');
    movements.flush(response([]));

    const movementPayload = {
      movementType: 'CASH_IN',
      direction: 'IN' as const,
      amount: 100,
      reference: 'REF-1',
      reason: 'Fondo adicional',
    };
    api.createMovement('session-1', movementPayload).subscribe();
    const movement = http.expectOne('/api/v1/cash/sessions/session-1/movements');
    expect(movement.request.method).toBe('POST');
    expect(movement.request.body).toEqual(movementPayload);
    movement.flush(response({ cashMovementId: 'movement-1' }));

    const closePayload = { countedCashAmount: 1100, notes: 'Cierre correcto' };
    api.closeSession('session-1', closePayload).subscribe();
    const close = http.expectOne('/api/v1/cash/sessions/session-1/close');
    expect(close.request.method).toBe('POST');
    expect(close.request.body).toEqual(closePayload);
    close.flush(response({ ...session, status: 'CLOSED' }));
  });

  const session = {
    cashSessionId: 'session-1',
    cashRegisterId: 'register-1',
    branchId: 'branch-1',
    cashRegisterCode: 'CAJA-1',
    cashRegisterName: 'Caja principal',
    openedBy: 'user-1',
    closedBy: null,
    status: 'OPEN',
    openingAmount: 1000,
    expectedAmount: 1000,
    countedAmount: null,
    differenceAmount: null,
    openedAt: '',
    closedAt: null,
    notes: 'Turno',
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
      correlationId: 'cash-test',
    };
  }
});
