import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { guestGuard } from './core/guards/guest-guard';
import { permissionGuard } from './core/guards/permission-guard';
import { operationalContextGuard } from './core/guards/operational-context-guard';

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
        redirectTo: 'catalog/products',
        pathMatch: 'full',
      },
      {
        path: 'catalog/products',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_PRODUCT_READ'],
        },
        loadComponent: () =>
          import('./features/catalog/products/pages/product-list/product-list').then(
            (module) => module.ProductList,
          ),
      },
      {
        path: 'catalog/products/new',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_PRODUCT_CREATE'],
        },
        loadComponent: () =>
          import('./features/catalog/products/pages/product-form/product-form').then(
            (module) => module.ProductForm,
          ),
      },
      {
        path: 'catalog/products/:productId/edit',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_PRODUCT_UPDATE'],
        },
        loadComponent: () =>
          import('./features/catalog/products/pages/product-form/product-form').then(
            (module) => module.ProductForm,
          ),
      },
      {
        path: 'catalog/products/:productId',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_PRODUCT_READ', 'CATALOG_PRESENTATION_READ'],
        },
        loadComponent: () =>
          import('./features/catalog/products/pages/product-detail/product-detail').then(
            (module) => module.ProductDetail,
          ),
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
        path: 'catalog/categories',
        canActivate: [permissionGuard],
        data: {
          permissions: ['CATALOG_CATEGORY_READ'],
        },
        loadComponent: () =>
          import('./features/catalog/categories/pages/category-list/category-list').then(
            (module) => module.CategoryList,
          ),
      },
      {
        path: 'inventory',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['INVENTORY_STOCK_READ'],
          operationalContext: 'WAREHOUSE',
          title: 'Inventario',
          description: 'Consulta stock, lotes, movimientos, ajustes y recepciones.',
          icon: 'warehouse',
        },
        loadComponent: () =>
          import('./features/inventory/pages/inventory-overview/inventory-overview').then(
            (module) => module.InventoryOverview,
          ),
      },
      {
        path: 'inventory/receipts/new',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['INVENTORY_RECEIPT_CREATE'],
          operationalContext: 'WAREHOUSE',
        },
        loadComponent: () =>
          import('./features/inventory/pages/inventory-receipt-form/inventory-receipt-form').then(
            (module) => module.InventoryReceiptForm,
          ),
      },
      {
        path: 'inventory/operations',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: [
            'INVENTORY_ADJUSTMENT_CREATE',
            'INVENTORY_TRANSFER_CREATE',
            'INVENTORY_COUNT_CREATE',
            'INVENTORY_RESERVATION_CREATE',
            'INVENTORY_EXPIRING_LOT_READ',
          ],
          permissionMatch: 'ANY',
          operationalContext: 'WAREHOUSE',
        },
        loadComponent: () =>
          import('./features/inventory/pages/advanced-inventory/advanced-inventory').then(
            (module) => module.AdvancedInventory,
          ),
      },
      {
        path: 'purchasing',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['PURCHASING_PURCHASE_READ', 'PURCHASING_SUPPLIER_READ'],
          permissionMatch: 'ANY',
          operationalContext: 'WAREHOUSE',
        },
        loadComponent: () =>
          import('./features/purchasing/pages/purchasing-overview/purchasing-overview').then(
            (module) => module.PurchasingOverview,
          ),
      },
      {
        path: 'purchasing/purchases/new',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['PURCHASING_PURCHASE_CREATE'],
          operationalContext: 'WAREHOUSE',
        },
        loadComponent: () =>
          import('./features/purchasing/pages/purchase-form/purchase-form').then(
            (module) => module.PurchaseForm,
          ),
      },
      {
        path: 'purchasing/purchases/:purchaseId',
        canActivate: [permissionGuard],
        data: { permissions: ['PURCHASING_PURCHASE_READ'] },
        loadComponent: () =>
          import('./features/purchasing/pages/purchase-detail/purchase-detail').then(
            (module) => module.PurchaseDetail,
          ),
      },
      {
        path: 'sales',
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['SALES_ORDER_READ'],
          operationalContext: 'WAREHOUSE',
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
        canActivate: [permissionGuard, operationalContextGuard],
        data: {
          permissions: ['CASH_SESSION_READ'],
          operationalContext: 'CASH_REGISTER',
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
