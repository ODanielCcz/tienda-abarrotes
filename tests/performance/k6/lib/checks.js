import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { apiUrl, config } from './config.js';

export const readLatencyMs = new Trend('read_latency_ms', true);
export const writeLatencyMs = new Trend('write_latency_ms', true);
export const unexpected5xx = new Counter('unexpected_5xx');
export const unexpectedFunctionalError = new Counter('unexpected_functional_error');
export const schemaError = new Counter('schema_error');

let requestCounter = 0;

export function apiRequest(method, path, token, expected) {
  const correlationId = nextCorrelationId(expected.name);
  const response = http.request(method, apiUrl(path), expected.body ? JSON.stringify(expected.body) : null, {
    headers: requestHeaders(token, correlationId),
    tags: { endpoint: expected.name, operation: expected.operation || method },
  });
  validateApiResponse(response, correlationId, path, expected);
  return response;
}

export function readinessRequest() {
  const correlationId = nextCorrelationId('readiness');
  const response = http.get(apiUrl('/actuator/health'), {
    headers: { 'X-Correlation-ID': correlationId },
    tags: { endpoint: 'readiness', operation: 'GET' },
  });
  // Actuator is not ApiResponseDto: it has no envelope correlation field, only the header.
  const body = parseEnvelope(response);
  const readinessBodyValid = body && body.status === 'UP';
  readLatencyMs.add(response.timings.duration);
  if (response.status >= 500) unexpected5xx.add(1);
  if (!readinessBodyValid) schemaError.add(1);
  check(response, {
    'readiness has expected HTTP status': (res) => res.status === 200,
    'readiness returns actuator UP JSON': () => readinessBodyValid,
    'readiness returns the correlation header': (res) => headerValue(res, 'X-Correlation-ID') === correlationId,
    'readiness has no 5xx': (res) => res.status < 500,
    'readiness meets latency budget': (res) => res.timings.duration <= config.maxReadLatencyMs,
  });
  return response;
}

export function validateApiResponse(response, correlationId, path, expected) {
  const isWrite = expected.operation === 'write';
  (isWrite ? writeLatencyMs : readLatencyMs).add(response.timings.duration);
  if (response.status >= 500) unexpected5xx.add(1);

  const envelope = parseEnvelope(response);
  const statusExpected = expected.statuses.includes(response.status);
  const envelopeValid = envelope
    && envelope.status === response.status
    && typeof envelope.code === 'string' && envelope.code.length > 0
    && typeof envelope.reason === 'string' && envelope.reason.length > 0
    && typeof envelope.timestamp === 'string' && !Number.isNaN(Date.parse(envelope.timestamp))
    && typeof envelope.message === 'string' && envelope.message.length > 0
    && Object.prototype.hasOwnProperty.call(envelope, 'data')
    && Object.prototype.hasOwnProperty.call(envelope, 'errors')
    && envelope.path === path.split('?')[0]
    && envelope.correlationId === correlationId
    && headerValue(response, 'X-Correlation-ID') === correlationId;
  if (!envelopeValid) schemaError.add(1);
  if (!statusExpected && response.status !== 409 && response.status !== 429) {
    unexpectedFunctionalError.add(1);
  }

  const latencyBudget = isWrite ? config.maxWriteLatencyMs : config.maxReadLatencyMs;
  check(response, {
    [`${expected.name} has expected HTTP status`]: () => statusExpected,
    [`${expected.name} has a valid API envelope`]: () => envelopeValid,
    [`${expected.name} has no 5xx`]: (res) => res.status < 500,
    [`${expected.name} meets latency budget`]: (res) => res.timings.duration <= latencyBudget,
  });
  return envelope;
}

export function requireApiEnvelope(response, name) {
  const envelope = parseEnvelope(response);
  if (!envelope || typeof envelope !== 'object') {
    schemaError.add(1);
    fail(`${name} did not return a readable API envelope.`);
  }
  return envelope;
}

export function requireApiData(response, name) {
  const envelope = requireApiEnvelope(response, name);
  if (envelope.data === null || typeof envelope.data === 'undefined') {
    schemaError.add(1);
    fail(`${name} did not return the required data.`);
  }
  return envelope.data;
}

export function requireApiField(data, field, name) {
  if (!data || typeof data !== 'object' || data[field] === null || typeof data[field] === 'undefined') {
    schemaError.add(1);
    fail(`${name} did not return the required ${field} field.`);
  }
  return data[field];
}

function parseEnvelope(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

function requestHeaders(token, correlationId) {
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    'X-Correlation-ID': correlationId,
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

function headerValue(response, name) {
  const expected = name.toLowerCase();
  const key = Object.keys(response.headers).find((candidate) => candidate.toLowerCase() === expected);
  return key ? response.headers[key] : undefined;
}

function nextCorrelationId(name) {
  requestCounter += 1;
  const iteration = typeof __ITER === 'undefined' ? 0 : __ITER;
  return `k6-${config.smokeRunId.slice(0, 8)}-${__VU}-${iteration}-${name}-${requestCounter}`;
}
