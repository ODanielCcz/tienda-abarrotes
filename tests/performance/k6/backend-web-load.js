import { setupProfile, executeProfile, teardownProfile, summaryFor } from './lib/profile-runtime.js';
import { loadProfile, stableSystemTags } from './lib/profile-config.js';

// The browser runner stops this via the loopback-only k6 control API.
// One hour is an upper bound, not an assertion that the browser covered one hour.
export const options = {
  scenarios: { default: { executor: 'constant-vus', vus: 25, duration: '1h', gracefulStop: '0s' } },
  thresholds: loadProfile.thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  systemTags: stableSystemTags,
};
export const setup = setupProfile;
export default function (session) { executeProfile(session); }
export const teardown = teardownProfile;
export function handleSummary(data) { return summaryFor(data, { name: 'backend-web-load' }); }
