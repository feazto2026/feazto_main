package com.codewild.food.shared.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Fine-grained permission scopes. Roles map to permission sets; privileged
 * endpoints require a permission, not just a role.
 *
 * <p>Mirrors MASTER spec §21 (Admin permission model). Keep this enum stable:
 * persisted audit logs reference these names.
 */
public enum Permission {
    // Marketplace / vendor
    VENDOR_VIEW,
    VENDOR_APPROVE,
    VENDOR_SUSPEND,
    MENU_MANAGE,
    MENU_VIEW,

    // Commerce
    ORDER_VIEW,
    ORDER_CREATE,
    ORDER_CANCEL,
    ORDER_REFUND,
    CART_MANAGE,

    // Fulfillment / rider
    RIDER_VIEW,
    RIDER_APPROVE,
    RIDER_SUSPEND,
    DELIVERY_ACCEPT,
    DELIVERY_UPDATE,

    // Finance
    FINANCE_VIEW,
    PAYOUT_MANAGE,
    REFUND_MANAGE,

    // Operations / support
    SUPPORT_VIEW,
    SUPPORT_ASSIGN,
    AUDIT_VIEW,
    SETTINGS_MANAGE,
    SERVICE_ZONE_MANAGE;

    /**
     * Canonical role -&gt; permission mapping. SUPER_ADMIN gets everything.
     * Adjust only via reviewed migration; never grant from client input.
     */
    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = Map.of(
            Role.CUSTOMER, Set.of(ORDER_VIEW, ORDER_CREATE, ORDER_CANCEL, CART_MANAGE, MENU_VIEW),
            Role.VENDOR, Set.of(MENU_MANAGE, MENU_VIEW, ORDER_VIEW),
            Role.RIDER, Set.of(ORDER_VIEW, DELIVERY_ACCEPT, DELIVERY_UPDATE),
            Role.ADMIN, Set.of(
                    VENDOR_VIEW, ORDER_VIEW, RIDER_VIEW, FINANCE_VIEW, SUPPORT_VIEW, AUDIT_VIEW),
            Role.SUPER_ADMIN, Collections.unmodifiableSet(EnumSet.allOf(Permission.class)),
            Role.OPS_ADMIN, Set.of(
                    VENDOR_VIEW, VENDOR_APPROVE, VENDOR_SUSPEND,
                    ORDER_VIEW, ORDER_CANCEL,
                    RIDER_VIEW, RIDER_APPROVE, RIDER_SUSPEND,
                    SUPPORT_VIEW, SUPPORT_ASSIGN, SERVICE_ZONE_MANAGE),
            Role.FINANCE_ADMIN, Set.of(
                    ORDER_VIEW, ORDER_REFUND, FINANCE_VIEW, PAYOUT_MANAGE, REFUND_MANAGE, AUDIT_VIEW),
            Role.SUPPORT_ADMIN, Set.of(
                    VENDOR_VIEW, ORDER_VIEW, RIDER_VIEW, SUPPORT_VIEW, SUPPORT_ASSIGN));

    public static Set<Permission> forRole(Role role) {
        if (role == null) {
            return Set.of();
        }
        return ROLE_PERMISSIONS.getOrDefault(role, Set.of());
    }

    public static boolean roleHas(Role role, Permission permission) {
        return forRole(role).contains(permission);
    }
}
