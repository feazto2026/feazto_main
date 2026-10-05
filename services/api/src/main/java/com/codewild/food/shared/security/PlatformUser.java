package com.codewild.food.shared.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated platform principal held in the Spring SecurityContext.
 *
 * <p>Built ONLY by server-side code ({@link PlatformUserService}) after a
 * validated Supabase JWT. Never deserialised from client input.
 *
 * <p><b>Entity vs principal:</b> the JPA entity
 * {@code com.codewild.food.identity.domain.PlatformUser} is the persisted
 * identity row; <em>this</em> class is the request-scoped authenticated
 * principal (roles resolved, phone masked). The service layer maps entity -&gt;
 * principal on every request so a role change takes effect on the next call
 * without waiting for token expiry.
 *
 * <p>Privacy: carries the minimum claims needed for authorization decisions.
 * Full PII (exact address, documents) is fetched per-request by domain services,
 * not stored in the security principal.
 */
public final class PlatformUser {

    private final UUID userId;
    private final String supabaseSub;
    private final String phoneE164;
    private final String phoneMasked;
    private final Set<Role> roles;
    private final Set<Permission> permissions;
    private final UUID customerId;
    private final UUID vendorId;
    private final VendorStatus vendorStatus;
    private final UUID riderId;
    private final RiderStatus riderStatus;

    public enum VendorStatus {
        NONE, PENDING, APPROVED, SUSPENDED, REJECTED
    }

    public enum RiderStatus {
        NONE, PENDING, APPROVED, SUSPENDED, REJECTED
    }

    private PlatformUser(Builder b) {
        this.userId = Objects.requireNonNull(b.userId, "userId");
        this.supabaseSub = Objects.requireNonNull(b.supabaseSub, "supabaseSub");
        this.phoneE164 = b.phoneE164;
        this.phoneMasked = mask(b.phoneE164);
        this.roles = Collections.unmodifiableSet(
                b.roles == null ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(b.roles));
        // Union of permissions across roles — computed server-side.
        EnumSet<Permission> perms = EnumSet.noneOf(Permission.class);
        for (Role r : this.roles) {
            perms.addAll(Permission.forRole(r));
        }
        this.permissions = Collections.unmodifiableSet(perms);
        this.customerId = b.customerId;
        this.vendorId = b.vendorId;
        this.vendorStatus = b.vendorStatus == null ? VendorStatus.NONE : b.vendorStatus;
        this.riderId = b.riderId;
        this.riderStatus = b.riderStatus == null ? RiderStatus.NONE : b.riderStatus;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSupabaseSub() {
        return supabaseSub;
    }

    /** Full E.164 phone. Avoid logging; prefer {@link #getPhoneMasked()}. */
    public String getPhoneE164() {
        return phoneE164;
    }

    /** Masked form like "+91 ••••• 3210" safe for logs and error messages. */
    public String getPhoneMasked() {
        return phoneMasked;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public VendorStatus getVendorStatus() {
        return vendorStatus;
    }

    public UUID getRiderId() {
        return riderId;
    }

    public RiderStatus getRiderStatus() {
        return riderStatus;
    }

    /** Vendor domain gate: only APPROVED vendors may trade. */
    public boolean isVendorApproved() {
        return vendorId != null && vendorStatus == VendorStatus.APPROVED;
    }

    /** Rider domain gate: only APPROVED riders may accept deliveries. */
    public boolean isRiderApproved() {
        return riderId != null && riderStatus == RiderStatus.APPROVED;
    }

    private static String mask(String e164) {
        if (e164 == null || e164.length() < 4) {
            return "••••";
        }
        String last4 = e164.substring(e164.length() - 4);
        return "•••• •••• " + last4;
    }

    public static Builder builder(UUID userId, String supabaseSub) {
        return new Builder(userId, supabaseSub);
    }

    public static final class Builder {
        private final UUID userId;
        private final String supabaseSub;
        private String phoneE164;
        private Set<Role> roles = EnumSet.noneOf(Role.class);
        private UUID customerId;
        private UUID vendorId;
        private VendorStatus vendorStatus = VendorStatus.NONE;
        private UUID riderId;
        private RiderStatus riderStatus = RiderStatus.NONE;

        private Builder(UUID userId, String supabaseSub) {
            this.userId = userId;
            this.supabaseSub = supabaseSub;
        }

        public Builder phoneE164(String v) {
            this.phoneE164 = v;
            return this;
        }

        public Builder roles(Set<Role> v) {
            this.roles = v == null ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(v);
            return this;
        }

        public Builder customerId(UUID v) {
            this.customerId = v;
            return this;
        }

        public Builder vendorId(UUID v) {
            this.vendorId = v;
            return this;
        }

        public Builder vendorStatus(VendorStatus v) {
            this.vendorStatus = v;
            return this;
        }

        public Builder riderId(UUID v) {
            this.riderId = v;
            return this;
        }

        public Builder riderStatus(RiderStatus v) {
            this.riderStatus = v;
            return this;
        }

        public PlatformUser build() {
            return new PlatformUser(this);
        }
    }
}
