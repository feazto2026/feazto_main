package com.codewild.food.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Ownership + state authorization checks. Called from controllers/services AFTER
 * authentication and coarse role/permission checks.
 *
 * <p>Principle (MASTER §28): {@code Authentication + Role + Permission + Ownership + State}.
 * Having a permission alone is never sufficient for someone else's resource.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Pure static methods over IDs so they are trivially unit-testable and leave
 *       entity fetching to the caller (avoids lazy-loading in a security class).</li>
 *   <li>All comparisons are null-safe and fail closed (return {@code false}).</li>
 *   <li>A Spring {@code @Component} wrapper is unnecessary; call these directly and
 *       translate {@code false} into 403 with a stable code
 *       ({@code FORBIDDEN}, {@code VENDOR_NOT_APPROVED}, {@code RIDER_NOT_APPROVED}).</li>
 * </ul>
 */
public final class PermissionEvaluator {

    private PermissionEvaluator() {
    }

    // -- generic -------------------------------------------------------

    public static boolean hasPermission(PlatformUser user, Permission permission) {
        return user != null && permission != null && user.hasPermission(permission);
    }

    public static boolean isOwner(UUID resourceOwnerUserId, PlatformUser user) {
        return user != null && resourceOwnerUserId != null
                && Objects.equals(resourceOwnerUserId, user.getUserId());
    }

    // -- commerce ------------------------------------------------------

    /** Customer may read/act on an order only if it is theirs (or they are privileged support/ops with ORDER_VIEW). */
    public static boolean canAccessOrder(PlatformUser user, UUID orderOwnerUserId) {
        if (user == null) {
            return false;
        }
        if (isOwner(orderOwnerUserId, user)) {
            return true;
        }
        // Privileged read path: explicit permission, still audited by the caller.
        return hasPermission(user, Permission.ORDER_VIEW) && user.getRoles().stream()
                .anyMatch(r -> r == Role.OPS_ADMIN || r == Role.SUPPORT_ADMIN
                        || r == Role.FINANCE_ADMIN || r == Role.SUPER_ADMIN || r == Role.ADMIN);
    }

    /** Vendor may act on an order item/vendor-order only for their own vendor profile AND when approved. */
    public static boolean canVendorAccessOrder(PlatformUser user, UUID orderVendorId) {
        if (user == null || orderVendorId == null || !user.isVendorApproved()) {
            return false;
        }
        return Objects.equals(orderVendorId, user.getVendorId());
    }

    // -- fulfillment ---------------------------------------------------

    /** Rider may update a delivery only when assigned AND approved. */
    public static boolean canRiderUpdateDelivery(PlatformUser user, UUID assignedRiderId) {
        if (user == null || assignedRiderId == null || !user.isRiderApproved()) {
            return false;
        }
        return Objects.equals(assignedRiderId, user.getRiderId());
    }

    // -- marketplace ---------------------------------------------------

    /** Vendor menu mutation gate: own menu + APPROVED status. */
    public static boolean canManageVendorMenu(PlatformUser user, UUID vendorId) {
        if (user == null || vendorId == null) {
            return false;
        }
        if (!hasPermission(user, Permission.MENU_MANAGE)) {
            return false;
        }
        return user.isVendorApproved() && Objects.equals(vendorId, user.getVendorId());
    }

    // -- admin ---------------------------------------------------------

    public static boolean canApproveVendor(PlatformUser user) {
        return hasPermission(user, Permission.VENDOR_APPROVE);
    }

    public static boolean canSuspendVendor(PlatformUser user) {
        return hasPermission(user, Permission.VENDOR_SUSPEND);
    }

    public static boolean canApproveRider(PlatformUser user) {
        return hasPermission(user, Permission.RIDER_APPROVE);
    }

    public static boolean canManagePayout(PlatformUser user) {
        return hasPermission(user, Permission.PAYOUT_MANAGE);
    }

    public static boolean canViewAudit(PlatformUser user) {
        return hasPermission(user, Permission.AUDIT_VIEW);
    }
}
