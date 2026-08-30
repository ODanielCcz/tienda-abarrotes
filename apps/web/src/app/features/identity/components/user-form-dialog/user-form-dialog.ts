import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs';

import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { IdentityApi } from '../../data-access/identity-api';
import { ManagedUser, RoleSummary } from '../../models/identity.model';

export interface UserFormDialogData {
  user: ManagedUser | null;
  roles: RoleSummary[];
}

@Component({
  selector: 'app-user-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './user-form-dialog.html',
  styleUrl: './user-form-dialog.scss',
})
export class UserFormDialog {
  private readonly api = inject(IdentityApi);
  private readonly ref = inject(MatDialogRef<UserFormDialog, ManagedUser | null>);
  protected readonly data = inject<UserFormDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly activeRoles = this.data.roles.filter((role) => role.status === 'ACTIVE');
  protected readonly form = this.fb.nonNullable.group({
    username: [this.data.user?.username ?? '', [Validators.required, Validators.maxLength(80)]],
    displayName: [
      this.data.user?.displayName ?? '',
      [Validators.required, Validators.maxLength(200)],
    ],
    password: [
      '',
      this.data.user === null
        ? [Validators.required, Validators.minLength(12), Validators.maxLength(255)]
        : [],
    ],
    roleCodes: this.fb.nonNullable.control<string[]>(this.data.user?.roles ?? []),
  });

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.submitting()) return;

    const value = this.form.getRawValue();
    const username = value.username.trim().toLowerCase();
    const displayName = value.displayName.trim();
    this.form.patchValue({ username, displayName });
    if (!username || !displayName) return;

    this.submitting.set(true);
    this.errorMessage.set(null);
    const operation = this.data.user
      ? this.api.updateUser(this.data.user.userId, { username, displayName })
      : this.api.createUser({
          username,
          displayName,
          password: value.password,
          roleCodes: value.roleCodes,
        });

    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (user) => this.ref.close(user),
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el usuario.')),
    });
  }
}
