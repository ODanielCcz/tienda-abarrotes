import { spikeProfile } from './lib/profile-config.js';
import {
  executeProfile, optionsFor, setupProfile, summaryFor, teardownProfile,
} from './lib/profile-runtime.js';

export const options = optionsFor(spikeProfile);
export const setup = setupProfile;
export default executeProfile;
export const teardown = teardownProfile;
export function handleSummary(data) { return summaryFor(data, spikeProfile); }
