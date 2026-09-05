import { config } from './config.js';
import exec from 'k6/execution';
import { loginInSetup, renewTokenInSetup, requireUsableSetupToken } from './auth.js';
import { readinessRequest } from './checks.js';
import { comparableSummary, stableSystemTags } from './profile-config.js';
import { executeWorkloadIteration } from './workload.js';

export function optionsFor(profile) {
  const thresholds = { ...profile.thresholds };
  for (const level of profile.levels || []) {
    const delay = `${level.startSeconds + Math.min(30, level.durationSeconds / 2)}s`;
    for (const [metric, threshold] of Object.entries({
      checks: 'rate>0.97', http_req_failed: 'rate<0.03',
      unexpected_5xx_rate: 'rate<0.01', unexpected_functional_error_rate: 'rate<0.03',
      schema_error: 'count==0',
    })) {
      thresholds[`${metric}{stress_level:${level.target}}`] = [
        { threshold, abortOnFail: true, delayAbortEval: delay },
      ];
    }
    thresholds[`http_req_duration{stress_level:${level.target}}`] = ['p(95)>=0'];
    thresholds[`http_reqs{stress_level:${level.target}}`] = ['count>=0'];
  }
  return {
    stages: profile.stages,
    gracefulRampDown: '0s',
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    systemTags: stableSystemTags,
  };
}

export function setupProfile() {
  readinessRequest();
  return renewTokenInSetup(loginInSetup(), 1800);
}

export function executeProfile(session, profile) {
  if (profile?.levels) {
    const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
    const level = profile.levels.find((candidate) =>
      elapsed < candidate.startSeconds + candidate.durationSeconds) || profile.levels.at(-1);
    exec.vu.metrics.tags.stress_level = String(level.target);
  }
  executeWorkloadIteration(requireUsableSetupToken(session));
}

export function teardownProfile() {
  // Timestamp emitted as traffic ends, before summary output or Compose cleanup.
  console.log(`PERF_TRAFFIC_STOPPED_AT=${Date.now()}`);
}

export function summaryFor(data, profile) {
  const summary = comparableSummary(data, profile.name, config.runId);
  if (profile.levels) {
    const elapsedSeconds = Number(data.state?.testRunDurationMs || 0) / 1000;
    summary.levels = profile.levels.map((level) => {
      const metrics = {};
      let failed = false;
      for (const [name, metric] of Object.entries(data.metrics)) {
        if (name.endsWith(`{stress_level:${level.target}}`)) {
          metrics[name.split('{')[0]] = metric.values;
          failed ||= Object.values(metric.thresholds || {}).some((value) => !value.ok);
        }
      }
      const reached = (metrics.http_reqs?.count || 0) > 0;
      const complete = elapsedSeconds >= level.startSeconds + level.durationSeconds;
      return { ...level, metrics, status: !reached ? 'NOT_REACHED'
        : failed ? 'THRESHOLD_BREACHED' : complete ? 'STABLE' : 'INCOMPLETE' };
    });
    summary.lastStableTarget = summary.levels.filter((level) => level.status === 'STABLE').at(-1)?.target ?? null;
  }
  const failures = failedCheckNames(data.root_group);
  return {
    stdout: [
      `${profile.name} ${config.runId}: ${summary.metrics.http_reqs.count} requests completed.`,
      failures.length === 0 ? 'Checks: PASS' : `Failed checks: ${failures.join(', ')}`,
      `Unexpected functional errors: ${summary.metrics.unexpected_functional_error?.count || 0}`,
      `Unexpected 5xx: ${summary.metrics.unexpected_5xx?.count || 0}`,
      '',
    ].join('\n'),
    [`/artifacts/${config.runId}-${profile.name.replace('-quick', '')}-summary.json`]: JSON.stringify(summary, null, 2),
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
