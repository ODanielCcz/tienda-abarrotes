import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import {
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  forkJoin,
  merge,
  of,
  switchMap,
} from 'rxjs';

import { AuthSessionStore } from '../../../../core/auth/auth-session-store';
import { ConfirmationDialog } from '../../../../shared/dialogs/confirmation-dialog/confirmation-dialog';
import { apiErrorMessage } from '../../../../shared/utilities/api-error-message';
import { OrganizationApi } from '../../../organization/data-access/organization-api';
import { Branch } from '../../../organization/models/organization.model';
import { RoleFormDialog } from '../../components/role-form-dialog/role-form-dialog';
import {
  UserAccessDialog,
  UserAccessMode,
} from '../../components/user-access-dialog/user-access-dialog';
import { UserFormDialog } from '../../components/user-form-dialog/user-form-dialog';
import { IdentityApi } from '../../data-access/identity-api';
import { IdentitySessionInvalidator } from '../../data-access/identity-session-invalidator';
import {
  ManagedUser,
  PermissionSummary,
  RoleSummary,
  UserAccountStatus,
} from '../../models/identity.model';

@Component({
  selector: 'app-identity-overview',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTabsModule,
  ],
  templateUrl: './identity-overview.html',
  styleUrl: './identity-overview.scss',
})
export class IdentityOverview {
  private readonly api = inject(IdentityApi);
  private readonly organizationApi = inject(OrganizationApi);
  private readonly session = inject(AuthSessionStore);
  private readonly invalidator = inject(IdentitySessionInvalidator);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly users = signal<ManagedUser[]>([]);
  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly permissions = signal<PermissionSummary[]>([]);
  protected readonly branches = signal<Branch[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly userColumns = [
    'username',
    'displayName',
    'roles',
    'branches',
    'status',
    'actions',
  ];
  protected readonly roleColumns = ['code', 'name', 'description', 'status', 'actions'];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly status = new FormControl<UserAccountStatus | 'ALL'>('ALL', {
    nonNullable: true,
  });

  protected readonly canReadUsers = this.session.hasPermission('IDENTITY_USER_READ');
  protected readonly canCreateUser = this.session.hasPermission('IDENTITY_USER_CREATE');
  protected readonly canUpdateUser = this.session.hasPermission('IDENTITY_USER_UPDATE');
  protected readonly canStatusUser = this.session.hasPermission('IDENTITY_USER_STATUS');
  protected readonly canPassword = this.session.hasPermission('IDENTITY_USER_PASSWORD_CHANGE');
  protected readonly canAssignRoles = this.session.hasPermission('IDENTITY_USER_ROLE_ASSIGN');
  protected readonly canAssignBranches = this.session.hasPermission('IDENTITY_USER_BRANCH_ASSIGN');
  protected readonly canReadRoles = this.session.hasPermission('IDENTITY_ROLE_READ');
  protected readonly canCreateRole = this.session.hasPermission('IDENTITY_ROLE_CREATE');
  protected readonly canUpdateRole = this.session.hasPermission('IDENTITY_ROLE_UPDATE');
  protected readonly canStatusRole = this.session.hasPermission('IDENTITY_ROLE_STATUS');
  protected readonly canAssignPermissions = this.session.hasPermission(
    'IDENTITY_ROLE_PERMISSION_ASSIGN',
  );

  constructor() {
    merge(
      this.search.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.status.valueChanges.pipe(distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadUsers());
    this.loadAll();
  }

  protected openUser(user: ManagedUser | null): void {
    this.dialog
      .open(UserFormDialog, {
        width: '46rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { user, roles: this.roles() },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.session.isAuthenticated()) this.loadUsers();
      });
  }

  protected manageUser(user: ManagedUser, mode: UserAccessMode): void {
    this.dialog
      .open(UserAccessDialog, {
        width: '46rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { user, mode, roles: this.roles(), branches: this.branches() },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.session.isAuthenticated()) this.loadUsers();
      });
  }

  protected changeUserStatus(user: ManagedUser): void {
    const next: UserAccountStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    this.confirm(
      'Cambiar estado del usuario',
      `${user.displayName} pasará de ${user.status} a ${next}.`,
    )
      .pipe(
        switchMap(() => this.api.changeUserStatus(user.userId, next)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({ next: () => this.loadUsers(), error: (error) => this.showError(error) });
  }

  protected openRole(role: RoleSummary | null): void {
    this.openRoleDialog(role, false);
  }

  protected managePermissions(role: RoleSummary): void {
    this.openRoleDialog(role, true);
  }

  protected changeRoleStatus(role: RoleSummary): void {
    if (role.system) return;
    const next = role.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.confirm('Cambiar estado del rol', `${role.name} pasará a ${next}.`)
      .pipe(
        switchMap(() => this.api.changeRoleStatus(role.roleId, next)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.invalidator.afterRoleAuthorizationChange(role.code);
          if (this.session.isAuthenticated()) this.loadRoles();
        },
        error: (error) => this.showError(error),
      });
  }

  protected refresh(): void {
    this.loadAll();
  }

  protected branchNames(ids: string[]): string {
    const names = ids.map(
      (id) => this.branches().find((branch) => branch.branchId === id)?.name ?? id,
    );
    return names.length ? names.join(', ') : 'Sin sucursal';
  }

  private openRoleDialog(role: RoleSummary | null, permissionMode: boolean): void {
    this.dialog
      .open(RoleFormDialog, {
        width: '50rem',
        maxWidth: '95vw',
        disableClose: true,
        data: { role, permissions: this.permissions(), permissionMode },
      })
      .afterClosed()
      .pipe(filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (role && permissionMode) {
          this.invalidator.afterRoleAuthorizationChange(role.code);
        }
        if (this.session.isAuthenticated()) this.loadRoles();
      });
  }

  private loadAll(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    forkJoin({
      users: this.canReadUsers ? this.userRequest() : of([]),
      roles: this.canReadRoles ? this.api.listRoles() : of([]),
      permissions: this.session.hasPermission('IDENTITY_PERMISSION_READ')
        ? this.api.listPermissions()
        : of([]),
      branches: this.session.hasPermission('ORGANIZATION_BRANCH_READ')
        ? this.organizationApi.listBranches('ACTIVE')
        : of([]),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ users, roles, permissions, branches }) => {
          this.users.set(users);
          this.roles.set(roles);
          this.permissions.set(permissions);
          this.branches.set(branches);
        },
        error: (error) => this.showError(error),
      });
  }

  private loadUsers(): void {
    if (!this.canReadUsers) return;
    this.loading.set(true);
    this.errorMessage.set(null);
    this.userRequest()
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (users) => this.users.set(users),
        error: (error) => this.showError(error),
      });
  }

  private loadRoles(): void {
    if (!this.canReadRoles) return;
    this.api
      .listRoles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (roles) => this.roles.set(roles),
        error: (error) => this.showError(error),
      });
  }

  private userRequest() {
    return this.api.listUsers({
      search: this.search.value.trim() || undefined,
      status: this.status.value === 'ALL' ? undefined : this.status.value,
    });
  }

  private confirm(title: string, message: string) {
    return this.dialog
      .open(ConfirmationDialog, {
        width: '28rem',
        data: { title, message, confirmLabel: 'Confirmar' },
      })
      .afterClosed()
      .pipe(filter(Boolean));
  }

  private showError(error: unknown): void {
    this.loading.set(false);
    this.errorMessage.set(apiErrorMessage(error, 'No fue posible completar la operación.'));
  }
}
