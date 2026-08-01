import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { guestGuard } from './core/guards/guest-guard';
import { permissionGuard } from './core/guards/permission-guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/authentication/pages/login/login').then((module) => module.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then((module) => module.AppShell),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/pages/dashboard/dashboard').then(
            (module) => module.Dashboard,
          ),
      },
      {
        path: 'catalog',
        redirectTo: 'catalog/brands',
        pathMatch: 'full',
      },
      {
        path: 'catalog/brands',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_BRAND_READ'],
        },
        loadComponent: () =>
          import('./features/catalog/brands/pages/brand-list/brand-list').then(
            (module) => module.BrandList,
          ),
      },
      {
        path: 'inventory',
        canActivate: [permissionGuard],
        data: {
          permissions: ['INVENTORY_STOCK_READ'],
          title: 'Inventario',
          description: 'Consulta stock, lotes, movimientos, ajustes y recepciones.',
          icon: 'warehouse',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'sales',
        canActivate: [permissionGuard],
        data: {
          permissions: ['SALES_ORDER_READ'],
          title: 'Ventas',
          description: 'Gestiona ventas, devoluciones y pagos asociados.',
          icon: 'point_of_sale',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'cash',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CASH_SESSION_READ'],
          title: 'Caja',
          description: 'Administra sesiones, movimientos y cortes de caja.',
          icon: 'payments',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'customers',
        canActivate: [permissionGuard],
        data: {
          permissions: ['SALES_CUSTOMER_READ'],
          title: 'Clientes',
          description: 'Consulta y administra clientes de la tienda.',
          icon: 'groups',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'reports',
        canActivate: [permissionGuard],
        data: {
          permissions: ['REPORT_SALES_READ'],
          title: 'Reportes',
          description: 'Visualiza ventas, inventario, caja, devoluciones y rentabilidad.',
          icon: 'monitoring',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'identity',
        canActivate: [permissionGuard],
        data: {
          permissions: ['IDENTITY_USER_READ'],
          title: 'Usuarios',
          description: 'Administra usuarios, roles, permisos y accesos por sucursal.',
          icon: 'admin_panel_settings',
        },
        loadComponent: () =>
          import('./shared/pages/module-placeholder/module-placeholder').then(
            (module) => module.ModulePlaceholder,
          ),
      },
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./shared/pages/forbidden/forbidden').then((module) => module.Forbidden),
      },
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard',
      },
      {
        path: '**',
        redirectTo: 'dashboard',
      },
    ],
  },
];
