import { apiRequest, requireApiData } from './checks.js';
import { config } from './config.js';

export function loginInSetup() {
  if (config.username !== 'perf_operator') {
    throw new Error('PERF_USERNAME must be perf_operator for the restricted performance scenario.');
  }
  if (!config.username || !config.password) {
    throw new Error('PERF_USERNAME and PERF_PASSWORD are required by the k6 Compose service.');
  }
  const response = apiRequest('POST', '/api/v1/auth/login', null, {
    name: 'login',
    operation: 'write',
    statuses: [200],
    body: { username: config.username, password: config.password },
  });
  const data = requireApiData(response, 'login');
  if (!data || !data.accessToken || !data.expiresAt) {
    throw new Error('Login response did not contain the required token metadata.');
  }
  return { accessToken: data.accessToken, expiresAt: data.expiresAt };
}

// k6 setup data is immutable for VUs. Long profiles must renew here, before the
// scenario begins, rather than creating unbounded login traffic during iterations.
export function renewTokenInSetup(session, minimumRemainingSeconds = 120) {
  const remainingSeconds = (Date.parse(session.expiresAt) - Date.now()) / 1000;
  return remainingSeconds > minimumRemainingSeconds ? session : loginInSetup();
}

export function requireUsableSetupToken(session) {
  if (Date.parse(session.expiresAt) <= Date.now()) {
    throw new Error('The setup JWT expired before the scenario started; rerun to renew it in setup.');
  }
  return session.accessToken;
}
