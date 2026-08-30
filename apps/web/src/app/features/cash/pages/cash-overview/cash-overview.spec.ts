import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { CashApi } from '../../data-access/cash-api';
import { CashOverview } from './cash-overview';

describe('CashOverview', () => {
  let fixture: ComponentFixture<CashOverview>;
  const api = {
    listSessions: vi.fn(),
    listMovements: vi.fn(),
    openSession: vi.fn(),
    createMovement: vi.fn(),
    closeSession: vi.fn(),
  };
  const context = {
    context: vi.fn(() => ({ branchId: 'branch-1', cashRegisterId: 'register-1' })),
    selectedCashRegister: vi.fn(() => ({ cashRegisterId: 'register-1', name: 'Caja principal' })),
  };
  const sessionStore = { hasPermission: vi.fn(() => true) };

  beforeEach(async () => {
    vi.clearAllMocks();
    api.listSessions.mockReturnValue(of([session]));
    api.listMovements.mockReturnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [CashOverview],
      providers: [
        { provide: CashApi, useValue: api },
        { provide: OperationalContextStore, useValue: context },
        { provide: AuthSessionStore, useValue: sessionStore },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CashOverview);
    fixture.detectChanges();
  });

  it('loads the open session and its movements for the selected register', () => {
    const component = expose(fixture);

    expect(api.listSessions).toHaveBeenCalledWith({ cashRegisterId: 'register-1' });
    expect(api.listMovements).toHaveBeenCalledWith('session-1');
    expect(component.currentSession()).toEqual(session);
  });

  it('creates a manual movement only against the open session', () => {
    const component = expose(fixture);
    component.movementForm.patchValue({
      movementType: 'CASH_IN',
      direction: 'IN',
      amount: 100,
      reference: 'REF-1',
      reason: 'Fondo adicional',
    });
    api.createMovement.mockReturnValueOnce(of({ cashMovementId: 'movement-1' }));

    component.createMovement();

    expect(api.createMovement).toHaveBeenCalledWith(
      'session-1',
      expect.objectContaining({ movementType: 'CASH_IN', direction: 'IN', amount: 100 }),
    );
  });

  it('closes the session using the counted amount', () => {
    const component = expose(fixture);
    component.closeForm.patchValue({ countedCashAmount: 1000, notes: 'Sin diferencia' });
    api.closeSession.mockReturnValueOnce(of({ ...session, status: 'CLOSED' }));

    component.closeSession();

    expect(api.closeSession).toHaveBeenCalledWith('session-1', {
      countedCashAmount: 1000,
      notes: 'Sin diferencia',
    });
  });

  function expose(target: ComponentFixture<CashOverview>) {
    return target.componentInstance as unknown as {
      currentSession: () => unknown;
      movementForm: FormGroup;
      closeForm: FormGroup;
      createMovement(): void;
      closeSession(): void;
    };
  }

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
    openedAt: '2026-08-29T00:00:00Z',
    closedAt: null,
    notes: 'Turno',
  };
});
