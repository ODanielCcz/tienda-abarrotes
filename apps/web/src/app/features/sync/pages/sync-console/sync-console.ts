import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { distinctUntilChanged, filter, finalize, forkJoin, Observable, of, switchMap } from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { OrganizationApi } from '../../../organization/data-access/organization-api';
import { Device } from '../../../organization/models/organization.model';
import { ConflictResolutionDialog } from '../../components/conflict-resolution-dialog/conflict-resolution-dialog';
import { SyncOperationDialog } from '../../components/sync-operation-dialog/sync-operation-dialog';
import { SyncApi } from '../../data-access/sync-api';
import { DeviceCheckpoint, OutboxBatch, SyncConflict } from '../../models/sync.model';

@Component({
  selector: 'app-sync-console',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './sync-console.html',
  styleUrl: './sync-console.scss',
})
export class SyncConsole {
  private readonly api = inject(SyncApi);
  private readonly organizationApi = inject(OrganizationApi);
  private readonly session = inject(AuthSessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly devices = signal<Device[]>([]);
  protected readonly checkpoint = signal<DeviceCheckpoint | null>(null);
  protected readonly outbox = signal<OutboxBatch | null>(null);
  protected readonly conflicts = signal<SyncConflict[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly deviceId = new FormControl<string | null>(null);
  protected readonly outboxColumns = ['sequence', 'event', 'aggregate', 'scope', 'createdAt'];
  protected readonly conflictColumns = ['sequence', 'type', 'operation', 'createdAt', 'actions'];
  protected readonly canInbox = this.can('SYNC_INBOX_WRITE');
  protected readonly canOutbox = this.can('SYNC_OUTBOX_READ');
  protected readonly canCheckpoint = this.can('SYNC_CHECKPOINT_READ');
  protected readonly canAcknowledge = this.can('SYNC_CHECKPOINT_ACK');
  protected readonly canReadConflicts = this.can('SYNC_CONFLICT_READ');
  protected readonly canResolve = this.can('SYNC_CONFLICT_RESOLVE');

  constructor() {
    this.organizationApi
      .listDevices(undefined, 'ACTIVE')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (devices) =>
          this.devices.set(devices.filter((device) => device.deviceType === 'MOBILE_EMPLOYEE')),
        error: (error) => this.showError(error),
      });
    this.deviceId.valueChanges
      .pipe(filter(Boolean), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadDevice());
  }
  protected can(permission: string): boolean {
    return this.session.hasPermission(permission);
  }
  protected selectedDevice(): Device | null {
    return this.devices().find((item) => item.deviceId === this.deviceId.value) ?? null;
  }
  protected refresh(): void {
    if (this.deviceId.value) this.loadDevice();
  }
  protected ingest(): void {
    const device = this.selectedDevice();
    const checkpoint = this.checkpoint();
    if (!device || !checkpoint) return;
    this.dialog
      .open(SyncOperationDialog, {
        width: '64rem',
        maxWidth: '96vw',
        disableClose: true,
        data: { device, checkpoint },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadDevice());
  }
  protected acknowledge(): void {
    const deviceId = this.deviceId.value;
    const batch = this.outbox();
    if (!deviceId || !batch) return;
    this.api
      .acknowledge(deviceId, batch.nextSequence)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.loadDevice(), error: (error) => this.showError(error) });
  }
  protected resolve(conflict: SyncConflict): void {
    this.dialog
      .open(ConflictResolutionDialog, {
        width: '50rem',
        maxWidth: '95vw',
        disableClose: true,
        data: conflict,
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadDevice());
  }
  private loadDevice(): void {
    const deviceId = this.deviceId.value;
    if (!deviceId) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.checkpoint.set(null);
    this.outbox.set(null);
    this.conflicts.set([]);
    const checkpointRequest: Observable<DeviceCheckpoint | null> = this.canCheckpoint
      ? this.api.getCheckpoint(deviceId)
      : of(null);
    checkpointRequest
      .pipe(
        switchMap((checkpoint) =>
          forkJoin({
            checkpoint: of(checkpoint),
            outbox: this.canOutbox
              ? this.api.getOutbox(deviceId, checkpoint?.lastAcknowledgedOutboxSequence, 100)
              : of(null),
            conflicts: this.canReadConflicts ? this.api.listConflicts(deviceId, false) : of([]),
          }),
        ),
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ checkpoint, outbox, conflicts }) => {
          this.checkpoint.set(checkpoint);
          this.outbox.set(outbox);
          this.conflicts.set(conflicts);
        },
        error: (error) => this.showError(error),
      });
  }
  private showError(error: unknown): void {
    this.loading.set(false);
    this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar la sincronización.'));
  }
}
