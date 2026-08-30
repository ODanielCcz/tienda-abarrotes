import { allowedConflictResolutions } from './sync.model';

describe('allowedConflictResolutions', () => {
  it('allows client or merged state only for carts', () => {
    expect(allowedConflictResolutions('CART_UPSERT')).toContain('CLIENT_WINS');
    expect(allowedConflictResolutions('CART_UPSERT')).toContain('MERGED');
    expect(allowedConflictResolutions('INVENTORY_COUNT_CREATE')).toEqual([
      'SERVER_WINS',
      'REJECTED',
    ]);
  });
});
