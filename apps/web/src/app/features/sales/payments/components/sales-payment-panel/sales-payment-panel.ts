import { DatePipe, DecimalPipe } from '@angular/common';
import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { filter, finalize, forkJoin, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../../core/context/operational-context.store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { CashApi } from '../../../../cash/data-access/cash-api';
import { CashSession } from '../../../../cash/models/cash.model';
import { SalesOrder } from '../../../models/sales.model';
import { SalesPaymentApi } from '../../data-access/sales-payment-api';
import { PaymentMethod, SalesPayment } from '../../models/sales-payment.model';

@Component({
  selector: 'app-sales-payment-panel',
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
  templateUrl: './sales-payment-panel.html',
  styleUrl: './sales-payment-panel.scss',
})
export class SalesPaymentPanel implements OnInit {
  readonly order = input.required<SalesOrder>();
  readonly changed = output<void>();

  private readonly api = inject(SalesPaymentApi);
  private readonly cashApi = inject(CashApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly payments = signal<SalesPayment[]>([]);
  protected readonly openCashSession = signal<CashSession | null>(null);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = ['paidAt', 'method', 'status', 'amount', 'reference', 'actions'];
  protected readonly canCreate = this.sessionStore.hasPermission('SALES_PAYMENT_CREATE');
  protected readonly canCancel = this.sessionStore.hasPermission('SALES_PAYMENT_CANCEL');
  protected readonly canReadCashSession = this.sessionStore.hasPermission('CASH_SESSION_READ');
  protected readonly capturedTotal = computed(() =>
    this.payments()
      .filter((payment) => payment.status === 'CAPTURED')
      .reduce((total, payment) => total + Number(payment.amount), 0),
  );
  protected readonly remainingBalance = computed(() =>
    Math.max(0, Number(this.order().total) - this.capturedTotal()),
  );
  protected idempotencyKey = crypto.randomUUID();
  protected readonly paymentForm = this.fb.nonNullable.group({
    paymentMethod: ['CASH', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    reference: [''],
  });

  ngOnInit(): void {
    this.paymentForm.controls.amount.setValue(Number(this.order().total));
    this.load();
  }

  protected createPayment(): void {
    if (
      !this.canCreate ||
      this.order().status !== 'CONFIRMED' ||
      this.paymentForm.invalid ||
      this.processing()
    )
      return;
    const value = this.paymentForm.getRawValue();
    const method = this.paymentMethod(value.paymentMethod);
    const amount = Number(value.amount);
    if (amount > this.remainingBalance()) {
      this.errorMessage.set('El pago no puede superar el saldo pendiente.');
      return;
    }
    if (method === 'CASH' && !this.openCashSession()) {
      this.errorMessage.set('Abre una sesión en la caja seleccionada antes de cobrar efectivo.');
      return;
    }

    this.processing.set(true);
    this.errorMessage.set(null);
    this.api
      .create(this.order().salesOrderId, {
        cashSessionId: method === 'CASH' ? this.openCashSession()!.cashSessionId : null,
        paymentMethod: method,
        amount,
        currencyCode: this.order().currencyCode,
        reference: this.optional(value.reference),
        idempotencyKey: this.idempotencyKey,
      })
      .pipe(
        finalize(() => this.processing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.idempotencyKey = crypto.randomUUID();
          this.changed.emit();
          this.load(false);
        },
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible registrar el pago.')),
      });
  }

  protected cancelPayment(payment: SalesPayment): void {
    if (!this.canCancel || payment.status !== 'CAPTURED' || this.processing()) return;
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: {
          title: 'Cancelar pago',
          message: 'La cancelación revertirá el movimiento de efectivo cuando aplique.',
          confirmLabel: 'Cancelar pago',
        },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.processing.set(true);
          return this.api
            .cancel(payment.paymentId)
            .pipe(finalize(() => this.processing.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.changed.emit();
          this.load(false);
        },
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible cancelar el pago.')),
      });
  }

  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.errorMessage.set(null);
    const registerId = this.contextStore.context().cashRegisterId;
    forkJoin({
      payments: this.api.list(this.order().salesOrderId),
      sessions:
        registerId && this.canReadCashSession
          ? this.cashApi.listSessions({ cashRegisterId: registerId, status: 'OPEN' })
          : of([] as CashSession[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ payments, sessions }) => {
          this.payments.set(payments);
          this.openCashSession.set(sessions[0] ?? null);
          const remaining = Math.max(
            0,
            Number(this.order().total) -
              payments
                .filter((payment) => payment.status === 'CAPTURED')
                .reduce((total, payment) => total + Number(payment.amount), 0),
          );
          this.paymentForm.controls.amount.setValue(remaining);
        },
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar los pagos.')),
      });
  }

  private paymentMethod(value: string): PaymentMethod {
    if (value === 'CARD' || value === 'TRANSFER') return value;
    return 'CASH';
  }

  private optional(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }
}
