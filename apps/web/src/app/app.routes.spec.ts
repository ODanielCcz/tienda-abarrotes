import { routes } from './app.routes';

describe('application routes', () => {
  it('protects the product catalog with the read permission', () => {
    const shell = routes.find((route) => route.path === '');
    const productRoute = shell?.children?.find((route) => route.path === 'catalog/products');

    expect(productRoute).toBeDefined();
    expect(productRoute?.data?.['permissions']).toEqual(['CATALOG_PRODUCT_READ']);
    expect(productRoute?.loadComponent).toBeDefined();
  });
});
