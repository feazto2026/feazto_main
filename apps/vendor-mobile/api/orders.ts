// Vendor / Home Cook app — orders integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).
import { ordersApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const orders = ordersApi(getAppClient());
export default orders;
