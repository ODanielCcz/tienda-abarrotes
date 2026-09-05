const quickLoad = __ENV.PERF_LOAD_QUICK === 'true';
const quickProfile = __ENV.PERF_PROFILE_QUICK === 'true';

const strictThresholds = {
  checks: ['rate==1'],
  http_req_failed: ['rate==0'],
  unexpected_5xx: ['count==0'],
  unexpected_functional_error: ['count==0'],
  schema_error: ['count==0'],
  read_p95: ['p(95)<500'],
  write_p95: ['p(95)<900'],
  business_p99: ['p(99)<1500'],
  load_completion: ['count>0'],
};

const breakpointThresholds = {
  checks: [{ threshold: 'rate>0.97', abortOnFail: true, delayAbortEval: '30s' }],
  http_req_failed: [{ threshold: 'rate<0.03', abortOnFail: true, delayAbortEval: '30s' }],
  unexpected_5xx_rate: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
  unexpected_functional_error_rate: [{ threshold: 'rate<0.03', abortOnFail: true, delayAbortEval: '30s' }],
  schema_error: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '30s' }],
  load_completion: ['count>0'],
};

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
  thresholds: strictThresholds,
};

export const stressProfile = {
  name: quickProfile ? 'backend-stress-quick' : 'backend-stress',
  levels: quickProfile ? [{ target: 2, startSeconds: 0, durationSeconds: 6 }]
    : [50, 75, 100, 150].map((target, index) => ({
      target, startSeconds: index * 240, durationSeconds: 240,
    })),
  stages: quickProfile ? [
    { duration: '1s', target: 2 },
    { duration: '4s', target: 2 },
    { duration: '1s', target: 0 },
  ] : [
    { duration: '1m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 75 },
    { duration: '3m', target: 75 },
    { duration: '1m', target: 100 },
    { duration: '3m', target: 100 },
    { duration: '1m', target: 150 },
    { duration: '3m', target: 150 },
  ],
  thresholds: breakpointThresholds,
};

export const spikeProfile = {
  name: quickProfile ? 'backend-spike-quick' : 'backend-spike',
  stages: quickProfile ? [
    { duration: '1s', target: 0 },
    { duration: '1s', target: 4 },
    { duration: '4s', target: 4 },
    { duration: '1s', target: 0 },
  ] : [
    { duration: '30s', target: 0 },
    { duration: '1s', target: 150 },
    { duration: '2m', target: 150 },
    { duration: '1s', target: 0 },
  ],
  thresholds: breakpointThresholds,
};

export const soakProfile = {
  name: quickProfile ? 'backend-soak-quick' : 'backend-soak',
  stages: quickProfile ? [
    { duration: '1s', target: 2 },
    { duration: '8s', target: 2 },
    { duration: '1s', target: 0 },
  ] : [
    { duration: '1m', target: 25 },
    { duration: '28m', target: 25 },
    { duration: '1m', target: 0 },
  ],
  thresholds: strictThresholds,
};

export const stableSystemTags = [
  'status', 'method', 'name', 'group', 'check', 'error', 'error_code',
  'expected_response', 'scenario',
];

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
    'unexpected_5xx_rate',
    'unexpected_functional_error',
    'unexpected_functional_error_rate',
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
