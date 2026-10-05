package com.codewild.food.shared.security;

/**
 * Platform roles. Single source of truth for RBAC.
 *
 * <p>Rules:
 * <ul>
 *   <li>Never trust a role claim sent by the client. Roles are loaded server-side
 *       by {@link PlatformUserService} from the authoritative PostgreSQL user/role tables.</li>
 *   <li>Supabase Auth is the identity provider (proves "who"). This enum decides
 *       "what they may do" in combination with {@link Permission}.</li>
 *   <li>Frontend role checks are UX-only and must never replace server enforcement.</li>
 * </ul>
 */
public enum Role {
    CUSTOMER,
    VENDOR,
    RIDER,
    ADMIN,
    SUPER_ADMIN,
    OPS_ADMIN,
    FINANCE_ADMIN,
    SUPPORT_ADMIN;

    /**
     * Returns true for any admin-family role.
     */
    public boolean isAdminFamily() {
        return switch (this) {
            case ADMIN, SUPER_ADMIN, OPS_ADMIN, FINANCE_ADMIN, SUPPORT_ADMIN -> true;
            default -> false;
        };
    }

    /**
     * Parse defensively: unknown or null strings map to null so callers fail closed.
     */
    public static Role parseOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
