import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { SyncApi } from '../../data-access/sync-api';
import { allowedConflictResolutions, SyncConflict, SyncResolution } from '../../models/sync.model';

@Component({
  selector: 'app-conflict-resolution-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './conflict-resolution-dialog.html',
  styleUrl: './conflict-resolution-dialog.scss',
})
export class ConflictResolutionDialog {
  private readonly api = inject(SyncApi);
  private readonly ref = inject(MatDialogRef<ConflictResolutionDialog, SyncConflict | null>);
  protected readonly conflict = inject<SyncConflict>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);
  protected readonly resolutions = allowedConflictResolutions(this.conflict.operationType);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    resolution: this.fb.nonNullable.control<SyncResolution>(this.resolutions[0]),
    resolutionNotes: ['', Validators.maxLength(1000)],
    mergedPayload: ['{}'],
  });
  protected submit(): void {
    if (this.submitting()) return;
    const value = this.form.getRawValue();
    let mergedPayload: Record<string, unknown> | null = null;
    if (value.resolution === 'MERGED') {
      try {
        const parsed: unknown = JSON.parse(value.mergedPayload);
        if (
          !parsed ||
          Array.isArray(parsed) ||
          typeof parsed !== 'object' ||
          Object.keys(parsed).length === 0
        )
          throw new Error();
        mergedPayload = parsed as Record<string, unknown>;
      } catch {
        this.errorMessage.set('MERGED requiere un objeto JSON no vacío.');
        return;
      }
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.api
      .resolveConflict(this.conflict.conflictId, {
        resolution: value.resolution,
        resolutionNotes: value.resolutionNotes.trim() || null,
        mergedPayload,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: (conflict) => this.ref.close(conflict),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No fue posible resolver el conflicto.')),
      });
  }
}
