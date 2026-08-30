import { contract, config } from './config.js';
import { sha256 } from 'k6/crypto';

function padded(value, length) {
  return String(value).padStart(length, '0');
}

function boundedIndex(seed, maximum) {
  const tail = seed.replace(/-/g, '').slice(-8);
  return (parseInt(tail, 16) % maximum) + 1;
}

// These formats deliberately mirror performance-fixtures.sql. Keeping the mapping here
// makes every fixture-derived identifier auditable without copying a static UUID list.
export function nonLotPresentationId(index) {
  assertFixtureIndex(index, contract.counts.nonLotPresentations, 'non-lot presentation');
  return `95100000-0000-0000-0001-${padded(index, 12)}`;
}

export function deviceId(index) {
  assertFixtureIndex(index, contract.counts.mobileDevices, 'mobile device');
  return `91000000-0000-0000-0001-${padded(index, 12)}`;
}

export function nonLotUnitPrice(index) {
  assertFixtureIndex(index, contract.counts.nonLotPresentations, 'non-lot presentation');
  // performance-fixtures.sql: 20 + (series_id % 25), valid on the GENERAL list.
  return 20 + (index % 25);
}

export function smokeFixtureSelection() {
  const presentationIndex = boundedIndex(config.smokeRunId, contract.counts.nonLotPresentations);
  const deviceIndex = (presentationIndex % contract.counts.mobileDevices) + 1;
  const initialAvailableQuantity = contract.warehouses[0].initialAvailableQuantity;
  if (!Number.isInteger(initialAvailableQuantity) || initialAvailableQuantity < 1) {
    throw new Error('The primary warehouse must define a positive initialAvailableQuantity.');
  }
  return {
    presentationIndex,
    productPresentationId: nonLotPresentationId(presentationIndex),
    unitPrice: nonLotUnitPrice(presentationIndex),
    deviceId: deviceId(deviceIndex),
    warehouseId: contract.warehouses[0].id,
    branchId: contract.branch.id,
    initialAvailableQuantity,
  };
}

export function deterministicUuid(label) {
  const hash = sha256(`${config.smokeRunId}:${label}`, 'hex');
  const variant = ['8', '9', 'a', 'b'][parseInt(hash[16], 16) % 4];
  return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-4${hash.slice(13, 16)}-${variant}${hash.slice(17, 20)}-${hash.slice(20, 32)}`;
}

function assertFixtureIndex(index, maximum, name) {
  if (!Number.isInteger(index) || index < 1 || index > maximum) {
    throw new Error(`Invalid ${name} fixture index: ${index}`);
  }
}
