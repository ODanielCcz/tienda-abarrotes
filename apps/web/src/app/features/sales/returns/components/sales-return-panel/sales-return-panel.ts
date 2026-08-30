import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { filter, finalize, forkJoin, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../../core/context/operational-context.store';
import { ConfirmationDialog } from '../../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../../shared/utilities/api-error-message';
import { CashApi } from '../../../../cash/data-access/cash-api';
import { CashSession } from '../../../../cash/models/cash.model';
import { SalesOrder } from '../../../models/sales.model';
import { SalesPaymentApi } from '../../../payments/data-access/sales-payment-api';
import { SalesPayment } from '../../../payments/models/sales-payment.model';
import { SalesReturnApi } from '../../data-access/sales-return-api';
import { SalesReturn } from '../../models/sales-return.model';

@Component({
  selector: 'app-sales-return-panel',
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
    MatTableModule,
  ],
  templateUrl: './sales-return-panel.html',
  styleUrl: './sales-return-panel.scss',
})
export class SalesReturnPanel implements OnInit {
  readonly order = input.required<SalesOrder>();
  readonly changed = output<void>();

  private readonly api = inject(SalesReturnApi);
  private readonly paymentApi = inject(SalesPaymentApi);
  private readonly cashApi = inject(CashApi);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly returns = signal<SalesReturn[]>([]);
  protected readonly openCashSession = signal<CashSession | null>(null);
  protected readonly hasCapturedCash = signal(false);
  protected readonly loading = signal(true);
  protected readonly processing = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly columns = ['createdAt', 'reason', 'status', 'total', 'actions'];
  protected readonly canCreate = this.sessionStore.hasPermission('SALES_RETURN_CREATE');
  protected readonly canConfirm = this.sessionStore.hasPermission('SALES_RETURN_CONFIRM');
  protected readonly canCancel = this.sessionStore.hasPermission('SALES_RETURN_CANCEL');
  protected readonly canReadPayments = this.sessionStore.hasPermission('SALES_PAYMENT_READ');
  protected readonly canReadCash = this.sessionStore.hasPermission('CASH_SESSION_READ');
  protected readonly returnForm = this.fb.group({
    reason: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(1000)]),
    items: this.fb.array<FormGroup>([]),
  });

  ngOnInit(): void {
    this.load();
  }

  protected get items(): FormArray<FormGroup> {
    return this.returnForm.controls.items;
  }

  protected createReturn(): void {
    if (!this.canCreate || this.returnForm.invalid || this.processing()) return;
    const selected = this.items
      .getRawValue()
      .filter((item) => Number(item['quantity']) > 0)
      .map((item) => ({
        salesOrderItemId: String(item['salesOrderItemId']),
        quantity: Number(item['quantity']),
      }));
    if (selected.length === 0) {
      this.errorMessage.set('Captura al menos una cantidad a devolver.');
      return;
    }
    this.process(
      this.api.create(this.order().salesOrderId, {
        reason: this.returnForm.controls.reason.value.trim(),
        items: selected,
      }),
      'No fue posible crear la devolución.',
      () => this.returnForm.controls.reason.reset(''),
    );
  }

  protected confirmReturn(salesReturn: SalesReturn): void {
    if (!this.canConfirm || salesReturn.status !== 'DRAFT' || this.processing()) return;
    if (this.hasCapturedCash() && !this.openCashSession()) {
      this.errorMessage.set('Abre una sesión de caja para confirmar el reembolso en efectivo.');
      return;
    }
    this.confirmAction(
      'Confirmar devolución',
      'Se repondrá inventario y se generará el reembolso correspondiente.',
      () =>
        this.api.confirm(salesReturn.returnId, {
          cashSessionId: this.hasCapturedCash() ? this.openCashSession()!.cashSessionId : null,
        }),
      'No fue posible confirmar la devolución.',
    );
  }

  protected cancelReturn(salesReturn: SalesReturn): void {
    if (!this.canCancel || salesReturn.status !== 'DRAFT' || this.processing()) return;
    this.confirmAction(
      'Cancelar devolución',
      'El borrador se cancelará sin modificar inventario ni caja.',
      () => this.api.cancel(salesReturn.returnId),
      'No fue posible cancelar la devolución.',
    );
  }

  private load(showSpinner = true): void {
    if (showSpinner) this.loading.set(true);
    this.errorMessage.set(null);
    const registerId = this.contextStore.context().cashRegisterId;
    forkJoin({
      returns: this.api.listByOrder(this.order().salesOrderId),
      payments: this.canReadPayments
        ? this.paymentApi.list(this.order().salesOrderId)
        : of([] as SalesPayment[]),
      sessions:
        registerId && this.canReadCash
          ? this.cashApi.listSessions({ cashRegisterId: registerId, status: 'OPEN' })
          : of([] as CashSession[]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ returns, payments, sessions }) => {
          this.returns.set(returns);
          this.hasCapturedCash.set(
            payments.some(
              (payment) => payment.status === 'CAPTURED' && payment.paymentMethod === 'CASH',
            ),
          );
          this.openCashSession.set(sessions[0] ?? null);
          this.buildItems(returns);
        },
        error: (error: unknown) =>
          this.errorMessage.set(
            apiErrorMessage(error, 'No fue posible consultar las devoluciones.'),
          ),
      });
  }

  private buildItems(returns: SalesReturn[]): void {
    this.items.clear();
    for (const orderItem of this.order().items) {
      const alreadyReturned = returns
        .filter((salesReturn) => salesReturn.status !== 'CANCELLED')
        .flatMap((salesReturn) => salesReturn.items)
        .filter((returnItem) => returnItem.salesOrderItemId === orderItem.salesOrderItemId)
        .reduce((total, returnItem) => total + Number(returnItem.quantity), 0);
      const remaining = Math.max(0, Number(orderItem.quantity) - alreadyReturned);
      if (remaining === 0) continue;
      this.items.push(
        this.fb.group({
          salesOrderItemId: this.fb.nonNullable.control(orderItem.salesOrderItemId),
          productName: this.fb.nonNullable.control(orderItem.productNameSnapshot),
          sku: this.fb.nonNullable.control(orderItem.skuSnapshot),
          remaining: this.fb.nonNullable.control(remaining),
          quantity: this.fb.nonNullable.control(0, [Validators.min(0), Validators.max(remaining)]),
        }),
      );
    }
  }

  private confirmAction(
    title: string,
    message: string,
    request: () => import('rxjs').Observable<SalesReturn>,
    fallback: string,
  ): void {
    this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: { title, message, confirmLabel: 'Confirmar' },
      })
      .afterClosed()
      .pipe(
        filter((confirmed) => confirmed === true),
        switchMap(() => {
          this.processing.set(true);
          return request().pipe(finalize(() => this.processing.set(false)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.changed.emit();
          this.load(false);
        },
        error: (error: unknown) => this.errorMessage.set(apiErrorMessage(error, fallback)),
      });
  }

  private process(
    request: import('rxjs').Observable<SalesReturn>,
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
          this.changed.emit();
          this.load(false);
        },
        error: (error: unknown) => this.errorMessage.set(apiErrorMessage(error, fallback)),
      });
  }
}
