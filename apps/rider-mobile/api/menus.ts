// Rider / Delivery Partner app — menus integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).
import { menusApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const menus = menusApi(getAppClient());
export default menus;
