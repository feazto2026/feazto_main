// Customer app — book-a-cook integration shim.
// Thin wrapper over packages/mobile-shared. No legacy UI copied here.
import { bookingsApi } from '../../../packages/mobile-shared/src/index.js';
import { getAppClient } from './_client.js';

export const bookings = bookingsApi(getAppClient());
export default bookings;
