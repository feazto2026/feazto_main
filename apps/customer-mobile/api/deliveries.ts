// Customer app — deliveries integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).
import { deliveriesApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const deliveries = deliveriesApi(getAppClient());
export default deliveries;
