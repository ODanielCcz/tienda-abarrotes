// The Compose service mounts the fixture contract read-only at this fixed location.
const fixtureContract = JSON.parse(open('/fixtures/fixture-contract.json'));

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const smokeRunId = __ENV.SMOKE_RUN_ID || '70000000-0000-4000-8000-000000000001';

if (!UUID_PATTERN.test(smokeRunId)) {
  throw new Error('SMOKE_RUN_ID must be a UUID so writes remain deterministic.');
}

export const contract = fixtureContract;
export const config = {
  baseUrl: (__ENV.BASE_URL || 'http://backend:8080').replace(/\/$/, ''),
  expectInvalidUrl: __ENV.EXPECT_INVALID_URL === 'true',
  expectAbortMidway: __ENV.EXPECT_ABORT_MIDWAY === 'true',
  username: __ENV.PERF_USERNAME || '',
  password: __ENV.PERF_PASSWORD || '',
  smokeRunId,
  maxReadLatencyMs: Number(__ENV.SMOKE_MAX_READ_LATENCY_MS || 1000),
  maxWriteLatencyMs: Number(__ENV.SMOKE_MAX_WRITE_LATENCY_MS || 2000),
};

export function apiUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return config.expectInvalidUrl
    ? `${config.baseUrl}/__k6_expected_invalid_url__${normalizedPath}`
    : `${config.baseUrl}${normalizedPath}`;
}
