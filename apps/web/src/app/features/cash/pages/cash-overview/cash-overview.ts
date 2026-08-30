import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { finalize, forkJoin, of } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { CashApi } from '../../data-access/cash-api';
import { CashMovement, CashSession } from '../../models/cash.model';

@Component({
  selector: 'app-cash-overview',
  imports: [
    DatePipe,
    DecimalPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './cash-overview.html',
  styleUrl: './cash-overview.scss',
})
export class CashOverview {
  private readonly api = inject(CashApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedCashRegister = this.contextStore.selectedCashRegister;
  protected readonly sessions = signal<CashSession[]>([]);
  protected readonly currentSession = signal<CashSession | null>(null);
  protected readonly movements = signal<CashMovement[]>([]);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly movementColumns = ['createdAt', 'type', 'direction', 'amount', 'reference'];
  protected readonly canOpen = this.sessionStore.hasPermission('CASH_SESSION_OPEN');
  protected readonly canClose = this.sessionStore.hasPermission('CASH_SESSION_CLOSE');
  protected readonly canReadMovements = this.sessionStore.hasPermission('CASH_MOVEMENT_READ');
  protected readonly canCreateMovement = this.sessionStore.hasPermission('CASH_MOVEMENT_CREATE');

  protected readonly openForm = this.fb.nonNullable.group({
    openingAmount: [0, [Validators.required, Validators.min(0)]],
    notes: [''],
  });
  protected readonly movementForm = this.fb.nonNullable.group({
    movementType: ['CASH_IN', Validators.required],
    direction: ['IN', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    reference: [''],
    reason: ['', [Validators.required, Validators.maxLength(1000)]],
  });
  protected readonly closeForm = this.fb.nonNullable.group({
    countedCashAmount: [0, [Validators.required, Validators.min(0)]],
    notes: [''],
  });

  constructor() {
    this.load();
  }

  protected refresh(): void {
    this.load();
  }

  protected openSession(): void {
    const cashRegisterId = this.contextStore.context().cashRegisterId;
    if (!cashRegisterId || this.openForm.invalid || this.currentSession() || this.processing())
      return;
    const value = this.openForm.getRawValue();
    this.process(
      this.api.openSession({
        cashRegisterId,
        openingAmount: Number(value.openingAmount),
        notes: this.optional(value.notes),
      }),
      'No fue posible abrir la caja.',
    );
  }

  protected createMovement(): void {
    const session = this.currentSession();
    if (!session || session.status !== 'OPEN' || this.movementForm.invalid || this.processing())
      return;
    const value = this.movementForm.getRawValue();
    this.process(
      this.api.createMovement(session.cashSessionId, {
        movementType: value.movementType,
        direction: value.direction === 'OUT' ? 'OUT' : 'IN',
        amount: Number(value.amount),
        reference: this.optional(value.reference),
        reason: this.optional(value.reason),
      }),
      'No fue posible registrar el movimiento de caja.',
      () =>
        this.movementForm.reset({
          movementType: 'CASH_IN',
          direction: 'IN',
          amount: 0,
          reference: '',
          reason: '',
        }),
    );
  }

  protected closeSession(): void {
    const session = this.currentSession();
    if (!session || session.status !== 'OPEN' || this.closeForm.invalid || this.processing())
      return;
    const value = this.closeForm.getRawValue();
    this.process(
      this.api.closeSession(session.cashSessionId, {
        countedCashAmount: Number(value.countedCashAmount),
        notes: this.optional(value.notes),
      }),
      'No fue posible cerrar la caja.',
    );
  }

  protected movementTypeChanged(type: string): void {
    if (type === 'CASH_IN') this.movementForm.controls.direction.setValue('IN');
    if (type === 'CASH_OUT') this.movementForm.controls.direction.setValue('OUT');
  }

  private load(): void {
    const cashRegisterId = this.contextStore.context().cashRegisterId;
    if (!cashRegisterId) {
      this.loading.set(false);
      this.errorMessage.set('Selecciona una caja registradora para continuar.');
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .listSessions({ cashRegisterId })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sessions) => {
          const open = sessions.find((session) => session.status === 'OPEN') ?? null;
          this.sessions.set(sessions);
          this.currentSession.set(open);
          if (!open || !this.canReadMovements) {
            this.movements.set([]);
            this.loading.set(false);
            return;
          }
          forkJoin({
            movements: this.api.listMovements(open.cashSessionId),
            session: of(open),
          })
            .pipe(
              finalize(() => this.loading.set(false)),
              takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
              next: ({ movements }) => this.movements.set(movements),
              error: (error: unknown) => this.showError(error, 'No fue posible consultar la caja.'),
            });
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.showError(error, 'No fue posible consultar las sesiones de caja.');
        },
      });
  }

  private process<T>(
    request: import('rxjs').Observable<T>,
    fallback: string,
    onSuccess?: () => void,
  ): void {
    this.processing.set(true);
    this.errorMessage.set(null);
    request
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          onSuccess?.();
          this.load();
        },
        error: (error: unknown) => this.showError(error, fallback),
      });
  }

  private optional(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }

  private showError(error: unknown, fallback: string): void {
    this.errorMessage.set(apiErrorMessage(error, fallback));
  }
}
