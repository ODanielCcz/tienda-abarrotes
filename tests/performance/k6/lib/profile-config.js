const quickLoad = __ENV.PERF_LOAD_QUICK === 'true';

export const loadProfile = {
  name: quickLoad ? 'backend-load-quick' : 'backend-load',
  stages: quickLoad ? [
    { duration: '2s', target: 1 },
    { duration: '6s', target: 1 },
    { duration: '2s', target: 0 },
  ] : [
    { duration: '1m', target: 10 },
    { duration: '2m', target: 10 },
    { duration: '1m', target: 25 },
    { duration: '2m', target: 25 },
    { duration: '1m', target: 50 },
    { duration: '4m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
    unexpected_5xx: ['count==0'],
    unexpected_functional_error: ['count==0'],
    schema_error: ['count==0'],
    read_p95: ['p(95)<500'],
    write_p95: ['p(95)<900'],
    business_p99: ['p(99)<1500'],
    load_completion: ['count>0'],
  },
};

export function comparableSummary(data, profileName, runId) {
  const metricNames = [
    'checks',
    'http_reqs',
    'http_req_failed',
    'http_req_duration',
    'read_p95',
    'write_p95',
    'business_p99',
    'unexpected_5xx',
    'unexpected_functional_error',
    'schema_error',
    'load_completion',
  ];
  const metrics = {};
  for (const name of metricNames) {
    if (data.metrics[name]) metrics[name] = data.metrics[name].values;
  }
  return {
    schemaVersion: 1,
    profile: profileName,
    runId,
    generatedAt: new Date().toISOString(),
    metrics,
  };
}
