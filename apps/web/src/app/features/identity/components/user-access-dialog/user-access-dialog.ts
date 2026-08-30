import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, Observable } from 'rxjs';

import { Branch } from '../../../organization/models/organization.model';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { IdentityApi } from '../../data-access/identity-api';
import { IdentitySessionInvalidator } from '../../data-access/identity-session-invalidator';
import { ManagedUser, RoleSummary } from '../../models/identity.model';

export type UserAccessMode = 'ROLES' | 'BRANCHES' | 'PASSWORD';

export interface UserAccessDialogData {
  user: ManagedUser;
  mode: UserAccessMode;
  roles: RoleSummary[];
  branches: Branch[];
}

@Component({
  selector: 'app-user-access-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './user-access-dialog.html',
  styleUrl: './user-access-dialog.scss',
})
export class UserAccessDialog {
  private readonly api = inject(IdentityApi);
  private readonly invalidator = inject(IdentitySessionInvalidator);
  private readonly ref = inject(MatDialogRef<UserAccessDialog, ManagedUser | null>);
  protected readonly data = inject<UserAccessDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    roleCodes: this.fb.nonNullable.control<string[]>(this.data.user.roles),
    branchIds: this.fb.nonNullable.control<string[]>(this.data.user.branchIds),
    password: [
      '',
      this.data.mode === 'PASSWORD'
        ? [Validators.required, Validators.minLength(12), Validators.maxLength(255)]
        : [],
    ],
  });

  protected title(): string {
    if (this.data.mode === 'ROLES') return 'Asignar roles';
    if (this.data.mode === 'BRANCHES') return 'Asignar sucursales';
    return 'Cambiar contraseña';
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.submitting()) return;
    const value = this.form.getRawValue();
    if (this.data.mode === 'PASSWORD' && this.form.controls.password.invalid) return;

    let operation: Observable<ManagedUser>;
    if (this.data.mode === 'ROLES') {
      operation = this.api.assignUserRoles(this.data.user.userId, value.roleCodes);
    } else if (this.data.mode === 'BRANCHES') {
      operation = this.api.assignUserBranches(this.data.user.userId, value.branchIds);
    } else {
      operation = this.api.changePassword(this.data.user.userId, value.password);
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (user) => {
        if (this.data.mode === 'ROLES' || this.data.mode === 'PASSWORD') {
          this.invalidator.afterAuthorizationChange(user.userId);
        }
        this.ref.close(user);
      },
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible actualizar el acceso.')),
    });
  }
}
