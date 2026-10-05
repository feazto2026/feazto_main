package com.codewild.food.shared.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Loads the authoritative {@link PlatformUser} for a validated JWT subject.
 *
 * <p>Contract for the backend team to back with repositories:
 * <pre>
 *   users(id, supabase_sub UNIQUE, phone_e164, status, created_at, ...)
 *   user_roles(user_id, role)                       -- server-managed only
 *   customer_profiles(id, user_id UNIQUE, ...)
 *   vendor_profiles(id, user_id UNIQUE, status, ...) -- PENDING/APPROVED/...
 *   rider_profiles(id, user_id UNIQUE, status, ...)
 * </pre>
 *
 * <p>Security rules enforced here:
 * <ul>
 *   <li>Roles come from {@code user_roles} only — the JWT {@code role} claim is
 *       informational (Supabase default) and MUST NOT be trusted.</li>
 *   <li>Unknown {@code sub} =&gt; empty: the filter returns 401, never a guest principal.</li>
 *   <li>Suspended/disabled users =&gt; empty (fail closed).</li>
 * </ul>
 *
 * <p>This file ships as an <em>interface + in-memory stub</em> so security wiring
 * compiles before repositories land. Production must bind the DB-backed bean.
 */
public interface PlatformUserService {

    Optional<PlatformUser> loadBySupabaseSub(String supabaseSub);

    // ------------------------------------------------------------------
    // Reference in-memory stub for local dev / contract tests only.
    // NOT for production: roles would be forgeable if this were used live.
    // ------------------------------------------------------------------
    class InMemoryStub implements PlatformUserService {
        private final PlatformUser fixed;

        public InMemoryStub(PlatformUser fixed) {
            this.fixed = fixed;
        }

        @Override
        public Optional<PlatformUser> loadBySupabaseSub(String supabaseSub) {
            if (fixed != null && fixed.getSupabaseSub().equals(supabaseSub)) {
                return Optional.of(fixed);
            }
            return Optional.empty();
        }

        /** Helper: approved vendor principal for local testing. */
        public static InMemoryStub approvedVendor(UUID userId, String sub, UUID vendorId) {
            PlatformUser u = PlatformUser.builder(userId, sub)
                    .phoneE164("+910000000000")
                    .roles(Set.of(Role.VENDOR))
                    .vendorId(vendorId)
                    .vendorStatus(PlatformUser.VendorStatus.APPROVED)
                    .build();
            return new InMemoryStub(u);
        }
    }
}
