import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthSessionStore } from '../../core/auth/auth-session-store';
import { AuthApi } from '../../features/authentication/data-access/auth-api';
import { ThemeStore } from '../../core/theme/theme-store';

interface NavigationItem {
  label: string;
  icon: string;
  route: string;
  permissions: string[];
}

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell {
  private readonly sessionStore = inject(AuthSessionStore);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly themeStore = inject(ThemeStore);

  protected readonly user = computed(() => this.sessionStore.currentUser());
  protected readonly isDarkTheme = computed(() => this.themeStore.isDark());

  protected toggleTheme(): void {
    this.themeStore.toggle();
  }

  protected readonly navigationItems = computed<NavigationItem[]>(() =>
    [
      {
        label: 'Dashboard',
        icon: 'dashboard',
        route: '/dashboard',
        permissions: [],
      },
      {
        label: 'Marcas',
        icon: 'sell',
        route: '/catalog/brands',
        permissions: ['CATALOG_BRAND_READ'],
      },
      {
        label: 'Categorías',
        icon: 'category',
        route: '/catalog/categories',
        permissions: ['CATALOG_CATEGORY_READ'],
      },
      {
        label: 'Inventario',
        icon: 'warehouse',
        route: '/inventory',
        permissions: ['INVENTORY_STOCK_READ', 'INVENTORY_MOVEMENT_READ'],
      },
      {
        label: 'Ventas',
        icon: 'point_of_sale',
        route: '/sales',
        permissions: ['SALES_ORDER_READ'],
      },
      {
        label: 'Caja',
        icon: 'payments',
        route: '/cash',
        permissions: ['CASH_SESSION_READ'],
      },
      {
        label: 'Clientes',
        icon: 'groups',
        route: '/customers',
        permissions: ['SALES_CUSTOMER_READ'],
      },
      {
        label: 'Reportes',
        icon: 'monitoring',
        route: '/reports',
        permissions: ['REPORT_SALES_READ', 'REPORT_INVENTORY_READ', 'REPORT_CASH_READ'],
      },
      {
        label: 'Usuarios',
        icon: 'admin_panel_settings',
        route: '/identity',
        permissions: ['IDENTITY_USER_READ', 'IDENTITY_ROLE_READ'],
      },
    ].filter((item) => this.sessionStore.hasAnyPermission(item.permissions)),
  );

  protected logout(): void {
    this.authApi.logout();
    void this.router.navigateByUrl('/login');
  }
}
