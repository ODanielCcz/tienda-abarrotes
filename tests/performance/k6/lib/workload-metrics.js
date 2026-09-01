import { Counter, Trend } from 'k6/metrics';

export const readP95 = new Trend('read_p95', true);
export const writeP95 = new Trend('write_p95', true);
export const businessP99 = new Trend('business_p99', true);
export const workloadRequests = new Counter('workload_requests');
export const loadCompletion = new Counter('load_completion');

export function recordWorkloadResponse(response, descriptor) {
  const success = descriptor.statuses.includes(response.status);
  const tags = {
    module: descriptor.module,
    endpoint: descriptor.name,
    operation: descriptor.operation,
    functional_result: success ? 'success' : 'unexpected',
  };
  workloadRequests.add(1, tags);
  businessP99.add(response.timings.duration, tags);
  (descriptor.operation === 'write' ? writeP95 : readP95).add(response.timings.duration, tags);
  return success;
}
