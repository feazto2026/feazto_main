package com.codewild.food.shared.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ownership-gate contract tests. Run with {@code ./mvnw test}.
 * No Spring context required — the evaluator is intentionally static/pure.
 */
class PermissionEvaluatorTest {

    private static PlatformUser vendor(UUID vendorId,
            PlatformUser.VendorStatus status) {
        return PlatformUser.builder(UUID.randomUUID(), "sub-" + UUID.randomUUID())
                .phoneE164("+911234567890")
                .roles(Set.of(Role.VENDOR))
                .vendorId(vendorId)
                .vendorStatus(status)
                .build();
    }

    @Test
    void approvedVendorManagesOwnMenuOnly() {
        UUID mine = UUID.randomUUID();
        PlatformUser me = vendor(mine, PlatformUser.VendorStatus.APPROVED);
        assertTrue(PermissionEvaluator.canManageVendorMenu(me, mine));
        assertFalse(PermissionEvaluator.canManageVendorMenu(me, UUID.randomUUID()));
    }

    @Test
    void pendingVendorIsGated() {
        UUID mine = UUID.randomUUID();
        PlatformUser me = vendor(mine, PlatformUser.VendorStatus.PENDING);
        assertFalse(PermissionEvaluator.canManageVendorMenu(me, mine));
        assertFalse(PermissionEvaluator.canVendorAccessOrder(me, mine));
    }

    @Test
    void riderMustBeAssignedAndApproved() {
        UUID riderId = UUID.randomUUID();
        PlatformUser approved = PlatformUser.builder(UUID.randomUUID(), "sub-r1")
                .roles(Set.of(Role.RIDER))
                .riderId(riderId)
                .riderStatus(PlatformUser.RiderStatus.APPROVED)
                .build();
        assertTrue(PermissionEvaluator.canRiderUpdateDelivery(approved, riderId));
        assertFalse(
                PermissionEvaluator.canRiderUpdateDelivery(approved, UUID.randomUUID()));

        PlatformUser suspended = PlatformUser.builder(UUID.randomUUID(), "sub-r2")
                .roles(Set.of(Role.RIDER))
                .riderId(riderId)
                .riderStatus(PlatformUser.RiderStatus.SUSPENDED)
                .build();
        assertFalse(PermissionEvaluator.canRiderUpdateDelivery(suspended, riderId));
    }

    @Test
    void customerCannotReadOthersOrderWithoutSupportRole() {
        PlatformUser customer = PlatformUser.builder(UUID.randomUUID(), "sub-c1")
                .roles(Set.of(Role.CUSTOMER))
                .build();
        // another user's order id-as-owner: denied
        assertFalse(PermissionEvaluator.canAccessOrder(customer, UUID.randomUUID()));
        // own orders: allowed
        assertTrue(PermissionEvaluator.canAccessOrder(customer, customer.getUserId()));
    }
}
