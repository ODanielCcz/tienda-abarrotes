import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthApi } from '../../../authentication/data-access/auth-api';

@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, MatIconModule, MatToolbarModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);

  protected logout(): void {
    this.authApi.logout();
    void this.router.navigateByUrl('/login');
  }
}
