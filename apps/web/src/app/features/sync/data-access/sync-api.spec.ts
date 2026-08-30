import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIG } from '../../../core/config/api.config';
import { SyncApi } from './sync-api';

describe('SyncApi', () => {
  let api: SyncApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_CONFIG, useValue: { baseUrl: '/api/v1' } },
      ],
    });
    api = TestBed.inject(SyncApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads checkpoint, outbox and conflicts for one device', () => {
    api.getCheckpoint('device-1').subscribe();
    http.expectOne('/api/v1/sync/devices/device-1/checkpoint').flush(response(checkpoint()));
    api.getOutbox('device-1', 4, 100).subscribe();
    http
      .expectOne(
        (request) =>
          request.url === '/api/v1/sync/outbox' &&
          request.params.get('deviceId') === 'device-1' &&
          request.params.get('afterSequence') === '4' &&
          request.params.get('limit') === '100',
      )
      .flush(response({ deviceId: 'device-1', afterSequence: 4, nextSequence: 5, events: [] }));
    api.listConflicts('device-1', false).subscribe();
    http.expectOne('/api/v1/sync/conflicts?deviceId=device-1&resolved=false').flush(response([]));
  });

  it('acknowledges outbox and resolves a conflict', () => {
    api.acknowledge('device-1', 8).subscribe();
    const ack = http.expectOne('/api/v1/sync/devices/device-1/checkpoint');
    expect(ack.request.method).toBe('POST');
    expect(ack.request.body).toEqual({ outboxSequence: 8 });
    ack.flush(response(checkpoint()));
    api
      .resolveConflict('conflict-1', {
        resolution: 'SERVER_WINS',
        resolutionNotes: 'Validado',
        mergedPayload: null,
      })
      .subscribe();
    const resolve = http.expectOne('/api/v1/sync/conflicts/conflict-1/resolve');
    expect(resolve.request.body.resolution).toBe('SERVER_WINS');
    resolve.flush(response({}));
  });

  it('ingests the exact operation envelope without replacing its idempotency key', () => {
    const request = {
      operationId: 'op-1',
      deviceId: 'device-1',
      deviceSequence: 5,
      idempotencyKey: 'key-1',
      operationType: 'CART_UPSERT' as const,
      aggregateType: 'CART',
      aggregateId: 'cart-1',
      payload: { items: [{ productPresentationId: 'presentation-1', quantity: 1 }] },
      clientCreatedAt: '2026-08-29T00:00:00Z',
    };
    api.ingest(request).subscribe();
    const ingest = http.expectOne('/api/v1/sync/inbox');
    expect(ingest.request.method).toBe('POST');
    expect(ingest.request.body).toEqual(request);
    ingest.flush(response({}));
  });

  function checkpoint() {
    return {
      deviceId: 'device-1',
      lastReceivedSequence: 4,
      lastProcessedSequence: 4,
      lastAcknowledgedOutboxSequence: 3,
      lastSyncAt: null,
      updatedAt: '2026-08-29T00:00:00Z',
    };
  }
  function response<T>(data: T) {
    return {
      timestamp: '',
      status: 200,
      code: 'OK',
      reason: 'OK',
      message: 'ok',
      data,
      errors: null,
      path: '',
      correlationId: 'sync-test',
    };
  }
});
