import { config } from './lib/config.js';
import { loginInSetup, renewTokenInSetup, requireUsableSetupToken } from './lib/auth.js';
import { readinessRequest } from './lib/checks.js';
import { comparableSummary, loadProfile } from './lib/profile-config.js';
import { executeWorkloadIteration } from './lib/workload.js';

export const options = {
  stages: loadProfile.stages,
  thresholds: loadProfile.thresholds,
};

export function setup() {
  readinessRequest();
  return renewTokenInSetup(loginInSetup(), 900);
}

export default function backendLoad(session) {
  executeWorkloadIteration(requireUsableSetupToken(session));
}

export function teardown() {
  readinessRequest();
}

export function handleSummary(data) {
  const summary = comparableSummary(data, loadProfile.name, config.runId);
  const failures = failedCheckNames(data.root_group);
  return {
    stdout: [
      `Backend load ${config.runId}: ${summary.metrics.http_reqs.count} requests completed.`,
      failures.length === 0 ? 'Checks: PASS' : `Failed checks: ${failures.join(', ')}`,
      `Unexpected functional errors: ${summary.metrics.unexpected_functional_error.count || 0}`,
      `Unexpected 5xx: ${summary.metrics.unexpected_5xx.count || 0}`,
      '',
    ].join('\n'),
    [`/artifacts/${config.runId}-load-summary.json`]: JSON.stringify(summary, null, 2),
  };
}

function failedCheckNames(group) {
  const direct = (group.checks || [])
    .filter((result) => result.fails > 0)
    .map((result) => `${result.name} (${result.fails})`);
  return Object.keys(group.groups || {}).reduce(
    (all, name) => all.concat(failedCheckNames(group.groups[name])),
    direct,
  );
}
