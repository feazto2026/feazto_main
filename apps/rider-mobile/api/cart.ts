// Rider / Delivery Partner app — cart integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).
import { cartApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const cart = cartApi(getAppClient());
export default cart;
