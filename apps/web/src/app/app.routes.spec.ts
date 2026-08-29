import { routes } from './app.routes';
import { operationalContextGuard } from './core/guards/operational-context-guard';

describe('application routes', () => {
  it('protects the product catalog with the read permission', () => {
    const shell = routes.find((route) => route.path === '');
    const productRoute = shell?.children?.find((route) => route.path === 'catalog/products');

    expect(productRoute).toBeDefined();
    expect(productRoute?.data?.['permissions']).toEqual(['CATALOG_PRODUCT_READ']);
    expect(productRoute?.loadComponent).toBeDefined();
  });

  it('protects product detail with product and presentation read permissions', () => {
    const shell = routes.find((route) => route.path === '');
    const detailRoute = shell?.children?.find(
      (route) => route.path === 'catalog/products/:productId',
    );

    expect(detailRoute).toBeDefined();
    expect(detailRoute?.data?.['permissions']).toEqual([
      'CATALOG_PRODUCT_READ',
      'CATALOG_PRESENTATION_READ',
    ]);
    expect(detailRoute?.loadComponent).toBeDefined();
  });

  it('protects product creation and editing with their write permissions', () => {
    const shell = routes.find((route) => route.path === '');
    const createRoute = shell?.children?.find(
      (route) => route.path === 'catalog/products/new',
    );
    const editRoute = shell?.children?.find(
      (route) => route.path === 'catalog/products/:productId/edit',
    );

    expect(createRoute?.data?.['permissions']).toEqual(['CATALOG_PRODUCT_CREATE']);
    expect(createRoute?.loadComponent).toBeDefined();
    expect(editRoute?.data?.['permissions']).toEqual(['CATALOG_PRODUCT_UPDATE']);
    expect(editRoute?.loadComponent).toBeDefined();

    const paths = shell?.children?.map((route) => route.path) ?? [];
    expect(paths.indexOf('catalog/products/new')).toBeLessThan(
      paths.indexOf('catalog/products/:productId'),
    );
    expect(paths.indexOf('catalog/products/:productId/edit')).toBeLessThan(
      paths.indexOf('catalog/products/:productId'),
    );
  });

  it('requires the appropriate operational context for inventory, sales and cash', () => {
    const shell = routes.find((route) => route.path === '');
    const inventory = shell?.children?.find((route) => route.path === 'inventory');
    const sales = shell?.children?.find((route) => route.path === 'sales');
    const cash = shell?.children?.find((route) => route.path === 'cash');

    expect(inventory?.data?.['operationalContext']).toBe('WAREHOUSE');
    expect(inventory?.canActivate).toContain(operationalContextGuard);
    expect(sales?.data?.['operationalContext']).toBe('WAREHOUSE');
    expect(sales?.canActivate).toContain(operationalContextGuard);
    expect(cash?.data?.['operationalContext']).toBe('CASH_REGISTER');
    expect(cash?.canActivate).toContain(operationalContextGuard);
  });
});
