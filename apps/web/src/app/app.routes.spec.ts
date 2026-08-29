import { routes } from './app.routes';

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
});
