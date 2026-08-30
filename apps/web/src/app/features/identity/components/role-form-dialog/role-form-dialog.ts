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
import { PermissionSummary, RoleDetail, RoleSummary } from '../../models/identity.model';

export interface RoleFormDialogData {
  role: RoleSummary | null;
  permissions: PermissionSummary[];
  permissionMode: boolean;
}

@Component({
  selector: 'app-role-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './role-form-dialog.html',
  styleUrl: './role-form-dialog.scss',
})
export class RoleFormDialog {
  private readonly api = inject(IdentityApi);
  private readonly ref = inject(MatDialogRef<RoleFormDialog, RoleDetail | null>);
  protected readonly data = inject<RoleFormDialogData>(MAT_DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  protected readonly loading = signal(this.data.permissionMode);
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly form = this.fb.nonNullable.group({
    code: [this.data.role?.code ?? '', [Validators.required, Validators.maxLength(80)]],
    name: [this.data.role?.name ?? '', [Validators.required, Validators.maxLength(150)]],
    description: [this.data.role?.description ?? '', Validators.maxLength(500)],
    permissionCodes: this.fb.nonNullable.control<string[]>([]),
  });

  constructor() {
    if (this.data.permissionMode && this.data.role) {
      this.api
        .getRole(this.data.role.roleId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (role) => this.form.controls.permissionCodes.setValue(role.permissions),
          error: (error: unknown) =>
            this.errorMessage.set(apiErrorMessage(error, 'No fue posible consultar el rol.')),
        });
    }
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading() || this.submitting()) return;
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.errorMessage.set(null);

    const operation =
      this.data.permissionMode && this.data.role
        ? this.api.assignRolePermissions(this.data.role.roleId, value.permissionCodes)
        : this.data.role
          ? this.api.updateRole(this.data.role.roleId, {
              code: value.code.trim().toUpperCase(),
              name: value.name.trim(),
              description: value.description.trim() || null,
            })
          : this.api.createRole({
              code: value.code.trim().toUpperCase(),
              name: value.name.trim(),
              description: value.description.trim() || null,
            });

    operation.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (role) => this.ref.close(role),
      error: (error: unknown) =>
        this.errorMessage.set(apiErrorMessage(error, 'No fue posible guardar el rol.')),
    });
  }
}
