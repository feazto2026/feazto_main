// Vendor / Home Cook app — auth integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).
import { authApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const auth = authApi(getAppClient());
export default auth;
