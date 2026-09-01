import { sha256 } from 'k6/crypto';
import { config, contract } from './config.js';
import { customerId, deviceId, nonLotPresentationId, nonLotUnitPrice } from './fixtures.js';

export function fixtureForVirtualUser(vu) {
  const presentationIndex = ((vu - 1) % contract.counts.nonLotPresentations) + 1;
  const customerIndex = ((vu - 1) % contract.counts.customers) + 1;
  const deviceIndex = ((vu - 1) % contract.counts.mobileDevices) + 1;
  return {
    branchId: contract.branch.id,
    warehouseId: contract.warehouses[0].id,
    productPresentationId: nonLotPresentationId(presentationIndex),
    unitPrice: nonLotUnitPrice(presentationIndex),
    customerId: customerId(customerIndex),
    deviceId: deviceId(deviceIndex),
  };
}

export function workloadUuid(label, vu = __VU, iteration = __ITER) {
  const hash = sha256(`${config.runId}:${label}:${vu}:${iteration}`, 'hex');
  const variant = ['8', '9', 'a', 'b'][parseInt(hash[16], 16) % 4];
  return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-4${hash.slice(13, 16)}-${variant}${hash.slice(17, 20)}-${hash.slice(20, 32)}`;
}
