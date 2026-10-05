package com.codewild.food.shared.security;

/**
 * Vendor / rider APPROVED gate.
 *
 * <p>Called by marketplace and fulfillment services before any trading action
 * (publish menu, accept order, accept delivery). Centralises the rule so no
 * controller can forget it:
 * <ul>
 *   <li>Vendor endpoints require {@link PlatformUser#isVendorApproved()}.</li>
 *   <li>Rider endpoints require {@link PlatformUser#isRiderApproved()}.</li>
 * </ul>
 * Translate {@link GateException#getCode()} to the API error envelope
 * ({@code VENDOR_NOT_APPROVED} / {@code RIDER_NOT_APPROVED}, HTTP 403).
 */
public final class VendorRiderApprovalGate {

    private VendorRiderApprovalGate() {
    }

    public static void requireApprovedVendor(PlatformUser user) throws GateException {
        if (user == null) {
            throw new GateException("FORBIDDEN", "Authentication required");
        }
        if (user.getVendorId() == null) {
            throw new GateException("FORBIDDEN", "Vendor profile required");
        }
        switch (user.getVendorStatus()) {
            case APPROVED -> {
                return;
            }
            case PENDING -> throw new GateException("VENDOR_NOT_APPROVED",
                    "Vendor verification is pending");
            case SUSPENDED -> throw new GateException("VENDOR_SUSPENDED",
                    "Vendor account is suspended");
            case REJECTED -> throw new GateException("VENDOR_NOT_APPROVED",
                    "Vendor verification was not approved");
            default -> throw new GateException("VENDOR_NOT_APPROVED",
                    "Vendor is not approved to trade");
        }
    }

    public static void requireApprovedRider(PlatformUser user) throws GateException {
        if (user == null) {
            throw new GateException("FORBIDDEN", "Authentication required");
        }
        if (user.getRiderId() == null) {
            throw new GateException("FORBIDDEN", "Rider profile required");
        }
        switch (user.getRiderStatus()) {
            case APPROVED -> {
                return;
            }
            case PENDING -> throw new GateException("RIDER_NOT_APPROVED",
                    "Rider verification is pending");
            case SUSPENDED -> throw new GateException("RIDER_SUSPENDED",
                    "Rider account is suspended");
            case REJECTED -> throw new GateException("RIDER_NOT_APPROVED",
                    "Rider verification was not approved");
            default -> throw new GateException("RIDER_NOT_APPROVED",
                    "Rider is not approved to deliver");
        }
    }

    /** Checked so callers consciously map it to the 403 envelope. */
    public static class GateException extends Exception {
        private final String code;

        public GateException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
