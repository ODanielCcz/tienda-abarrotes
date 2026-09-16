const fs = require('node:fs');
const path = require('node:path');
const baseURL = 'http://localhost:54201';
function localRoot() {
  if (!process.env.LOCALAPPDATA) throw new Error('LOCALAPPDATA is required');
  return path.resolve(process.env.LOCALAPPDATA, 'TiendaAbarrotes', 'performance');
}
function parseEnvironment(text) {
  return Object.fromEntries(text
    .split(/\r?\n/).filter((line) => /^[A-Z][A-Z0-9_]*=/.test(line))
    .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
}
function credentials(admin = false) {
  const values = parseEnvironment(fs.readFileSync(path.join(localRoot(), 'perf.env'), 'utf8'));
  const username = values[admin ? 'E2E_USERNAME' : 'PERF_USERNAME'];
  const password = values[admin ? 'E2E_PASSWORD' : 'PERF_FIXTURE_USER_PASSWORD'];
  if (username !== (admin ? 'perf_admin' : 'perf_operator') || !password)
    throw new Error('Only isolated performance fixture credentials are allowed');
  return { username, password };
}
function artifactDirectory() {
  const root = path.join(localRoot(), 'artifacts', 'web');
  const id = process.env.PERF_WEB_RUN_ID;
  if (!id || !/^[a-zA-Z0-9-]+$/.test(id)) throw new Error('PERF_WEB_RUN_ID is required');
  return path.join(root, id);
}
function sanitize(value) {
  return String(value).replace(/Bearer\s+\S+/gi, 'Bearer [REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED_JWT]');
}
module.exports = { baseURL, credentials, artifactDirectory, sanitize, parseEnvironment };
