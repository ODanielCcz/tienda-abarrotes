import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { OperationalContextStore } from '../../../../core/context/operational-context.store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { MetricCard } from '../../components/metric-card/metric-card';
import { PeriodFilter, PeriodSelection } from '../../components/period-filter/period-filter';
import { DashboardApi, DashboardSnapshot } from '../../data-access/dashboard-api';

@Component({
  selector: 'app-dashboard',
  imports: [
    DecimalPipe,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    MetricCard,
    PeriodFilter,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(DashboardApi);
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly contextStore = inject(OperationalContextStore);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly selectedBranch = this.contextStore.selectedBranch;
  protected readonly selectedWarehouse = this.contextStore.selectedWarehouse;
  protected readonly snapshot = signal<DashboardSnapshot | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly from = signal(this.dateDaysAgo(29));
  protected readonly to = signal(this.dateDaysAgo(0));
  private readonly includeSales = this.sessionStore.hasPermission('REPORT_SALES_READ');
  private readonly includeMargin = this.sessionStore.hasPermission('REPORT_GROSS_MARGIN_READ');
  private readonly includeInventory = this.sessionStore.hasPermission('REPORT_INVENTORY_READ');
  private readonly includeCash = this.sessionStore.hasPermission('REPORT_CASH_READ');

  constructor() {
    this.load();
  }

  protected applyPeriod(period: PeriodSelection): void {
    this.from.set(period.from);
    this.to.set(period.to);
    this.load();
  }

  protected refresh(): void {
    this.load();
  }

  protected openCashSessionCount(sessions: DashboardSnapshot['cashSessions']): number {
    return sessions.filter((session) => session.status === 'OPEN').length;
  }

  private load(): void {
    const context = this.contextStore.context();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.api
      .load({
        from: this.from(),
        to: this.to(),
        ...(context.branchId ? { branchId: context.branchId } : {}),
        ...(context.warehouseId ? { warehouseId: context.warehouseId } : {}),
        includeSales: this.includeSales,
        includeMargin: this.includeMargin,
        includeInventory: this.includeInventory,
        includeCash: this.includeCash,
      })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (snapshot) => this.snapshot.set(snapshot),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible cargar el dashboard.')),
      });
  }

  private dateDaysAgo(days: number): string {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString().slice(0, 10);
  }
}
