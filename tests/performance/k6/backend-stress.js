import { stressProfile } from './lib/profile-config.js';
import {
  executeProfile, optionsFor, setupProfile, summaryFor, teardownProfile,
} from './lib/profile-runtime.js';

export const options = optionsFor(stressProfile);
export const setup = setupProfile;
export default function (session) { executeProfile(session, stressProfile); }
export const teardown = teardownProfile;
export function handleSummary(data) { return summaryFor(data, stressProfile); }
