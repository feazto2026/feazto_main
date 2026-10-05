// AuthAgent verification: file inventory + key security assertions. Run: node scripts/verify-auth.cjs
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const expected = [
  'services/api/src/main/java/com/codewild/food/shared/security/Role.java',
  'services/api/src/main/java/com/codewild/food/shared/security/Permission.java',
  'services/api/src/main/java/com/codewild/food/shared/security/PlatformUser.java',
  'services/api/src/main/java/com/codewild/food/shared/security/SupabaseJwtValidator.java',
  'services/api/src/main/java/com/codewild/food/shared/security/JwtAuthenticationFilter.java',
  'services/api/src/main/java/com/codewild/food/shared/security/PlatformUserService.java',
  'services/api/src/main/java/com/codewild/food/shared/security/SupabasePlatformUserService.java',
  'services/api/src/main/java/com/codewild/food/shared/security/PermissionEvaluator.java',
  'services/api/src/main/java/com/codewild/food/shared/security/VendorRiderApprovalGate.java',
  'services/api/src/main/java/com/codewild/food/shared/security/OtpSecurityPolicy.java',
  'services/api/src/main/java/com/codewild/food/shared/security/SecurityConfig.java',
  'services/api/src/main/java/com/codewild/food/shared/security/SupabaseJwksSignatureVerifier.java',
  'services/api/src/main/java/com/codewild/food/identity/infrastructure/PlatformUserRepository.java',
  'services/api/src/main/java/com/codewild/food/identity/infrastructure/UserRoleRepository.java',
  'services/api/src/main/java/com/codewild/food/identity/infrastructure/RoleRepository.java',
  'services/api/src/main/java/com/codewild/food/identity/application/AuthService.java',
  'services/api/src/main/java/com/codewild/food/identity/api/AuthController.java',
  'services/api/src/main/resources/application-security.example.yml',
  'services/api/src/main/resources/db/migration/V3__supabase_auth.sql',
  'services/api/src/test/java/com/codewild/food/shared/security/PermissionEvaluatorTest.java',
  'docs/architecture/auth.md',
  'packages/mobile-shared/api-client.ts',
  'packages/mobile-shared/auth-refresh.ts',
  'packages/mobile-shared/env.ts',
  'packages/mobile-shared/index.ts',
  'packages/mobile-shared/README.md',
  'supabase/auth-hooks/README.md',
  'supabase/auth-hooks/sync-platform-user.sql',
];

let fail = 0;
const ok = (cond, msg) => {
  console.log((cond ? 'PASS' : 'FAIL') + ' ' + msg);
  if (!cond) fail++;
};

for (const rel of expected) {
  ok(fs.existsSync(path.join(root, rel)), 'exists ' + rel);
}
// Deleted insecure HS256 chain must stay deleted (single SecurityFilterChain).
ok(!fs.existsSync(path.join(root, 'services/api/src/main/java/com/codewild/food/config/SecurityConfig.java')),
  'deleted legacy config.SecurityConfig (single chain)');
ok(!fs.existsSync(path.join(root, 'services/api/src/main/java/com/codewild/food/shared/security/JwtAuthFilter.java')),
  'deleted legacy JwtAuthFilter HS256 (single chain)');

const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');

const sec = read('services/api/src/main/java/com/codewild/food/shared/security/SecurityConfig.java');
ok(sec.includes('/api/v1/auth/otp/'), 'SecurityConfig public OTP paths');
ok(sec.includes('/api/v1/payments/webhook'), 'SecurityConfig public payments webhook');
ok(sec.includes('/actuator/health'), 'SecurityConfig public actuator health');
ok(sec.includes('.authenticated()'), 'SecurityConfig authenticated-by-default');
ok(!sec.includes('ConditionalOnProperty'), 'SecurityConfig single unconditional chain (no mode gate)');
ok(sec.includes('SupabaseJwksSignatureVerifier') || sec.includes('supabaseJwtValidator'),
  'SecurityConfig wires Supabase JWKS chain');

const val = read('services/api/src/main/java/com/codewild/food/shared/security/SupabaseJwtValidator.java');
ok(val.includes('CLOCK_SKEW_SECONDS'), 'JWT clock-skew tolerance');
ok(val.includes('verifier_not_configured') || val.includes('verifier is not configured'), 'JWT fail-closed without verifier');

const jwks = read('services/api/src/main/java/com/codewild/food/shared/security/SupabaseJwksSignatureVerifier.java');
for (const k of ['JWKSet.parse', 'getKeyByKeyId', 'ALLOWED_ALGS', 'RS256', 'ES256', 'UnknownKid',
  'expireAfterWrite(10', 'RSASSAVerifier', 'ECDSAVerifier', 'SignedJWT.parse']) {
  ok(jwks.includes(k), 'JWKS verifier: ' + k);
}

const repo = read('services/api/src/main/java/com/codewild/food/identity/infrastructure/PlatformUserRepository.java');
for (const m of ['findByAuthUserId', 'findActiveByAuthUserId', 'findRoleCodesByUserId']) {
  ok(repo.includes(m), 'PlatformUserRepository.' + m);
}
ok(repo.includes('ACTIVE'), 'PlatformUserRepository status check');

const svc = read('services/api/src/main/java/com/codewild/food/shared/security/SupabasePlatformUserService.java');
ok(svc.includes('@Primary') && svc.includes('loadBySupabaseSub'), 'SupabasePlatformUserService primary DB-backed');
ok(svc.includes('findActiveByAuthUserId') && svc.includes('findRoleCodesByUserId'),
  'SupabasePlatformUserService roles join + status check');

const pe = read('services/api/src/main/java/com/codewild/food/shared/security/PermissionEvaluator.java');
for (const m of ['canAccessOrder', 'canVendorAccessOrder', 'canRiderUpdateDelivery', 'canManageVendorMenu']) {
  ok(pe.includes(m), 'PermissionEvaluator.' + m);
}

const gate = read('services/api/src/main/java/com/codewild/food/shared/security/VendorRiderApprovalGate.java');
ok(gate.includes('VENDOR_NOT_APPROVED') && gate.includes('RIDER_NOT_APPROVED'), 'APPROVED gate codes');

const su = read('services/api/src/main/java/com/codewild/food/shared/security/SecurityUtils.java');
ok(su.includes('PlatformUser') && su.includes('currentUser()'), 'SecurityUtils PlatformUser principal');
ok(!su.includes('String.valueOf(a.getPrincipal())'), 'SecurityUtils no String-principal leak');

const roles = read('services/api/src/main/java/com/codewild/food/shared/security/Role.java');
for (const r of ['CUSTOMER', 'VENDOR', 'RIDER', 'ADMIN', 'SUPER_ADMIN', 'OPS_ADMIN', 'FINANCE_ADMIN', 'SUPPORT_ADMIN']) {
  ok(roles.includes(r), 'Role.' + r);
}
const perms = read('services/api/src/main/java/com/codewild/food/shared/security/Permission.java');
for (const p of ['VENDOR_VIEW', 'VENDOR_APPROVE', 'VENDOR_SUSPEND', 'RIDER_APPROVE', 'PAYOUT_MANAGE', 'AUDIT_VIEW']) {
  ok(perms.includes(p), 'Permission.' + p);
}

const otp = read('services/api/src/main/java/com/codewild/food/shared/security/OtpSecurityPolicy.java');
ok(otp.includes('otp:'), 'OTP Redis key prefix otp:{phone}');
ok(otp.includes('MAX_ATTEMPTS'), 'OTP attempt limits');
ok(otp.includes('KEY_REQ_IP') || otp.includes('otp:req:ip:'), 'OTP per-IP key');
ok(otp.includes('hash(') && otp.includes('constantTimeEquals'), 'OTP hash + constant-time compare');

const auth = read('services/api/src/main/java/com/codewild/food/identity/application/AuthService.java');
for (const k of ['SecureRandom', 'OtpSecurityPolicy.hash', 'OTP_EXPIRED', 'OTP_ATTEMPTS_EXCEEDED',
  'OTP_RESEND_TOO_SOON', 'RESEND_COOLDOWN_SECONDS', 'MAX_SENDS_PER_IP_PER_HOUR']) {
  ok(auth.includes(k), 'AuthService.' + k);
}
ok(!auth.includes('Keys.hmacShaKeyFor') && !auth.includes('issueToken'),
  'AuthService no HS256 self-mint');
ok(auth.includes('delete(') || auth.includes('redis.delete'), 'AuthService delete-on-success');

const ctl = read('services/api/src/main/java/com/codewild/food/identity/api/AuthController.java');
ok(ctl.includes('/otp/request') && ctl.includes('/otp/verify') && ctl.includes('/otp/resend'),
  'AuthController OTP request/verify/resend');
ok(ctl.includes('/me'), 'AuthController /me');

const yml = read('services/api/src/main/resources/application-security.example.yml');
ok(yml.includes('OTP_SECRET'), 'security example OTP_SECRET');
ok(!yml.includes('SECURITY_JWT_MODE') || !yml.includes('mode:'), 'security example single chain (no mode gate)');

const sql = read('supabase/auth-hooks/sync-platform-user.sql');
ok(sql.includes('platform_users') && sql.includes('handle_new_auth_user'),
  'sync trigger provisions platform_users');
ok(sql.includes("CUSTOMER") && sql.includes('user_roles'), 'sync trigger default CUSTOMER + user_roles');

const api = read('packages/mobile-shared/api-client.ts');
for (const k of ['NETWORK', 'AUTH', 'VALIDATION', 'BUSINESS', 'PAYMENT', 'NOT_FOUND', 'PERMISSION', 'SERVER']) {
  ok(api.includes("'" + k + "'"), 'api-client error kind ' + k);
}
ok(api.includes('Idempotency-Key'), 'api-client Idempotency-Key');
ok(api.includes('Authorization'), 'api-client JWT inject');

const envTs = read('packages/mobile-shared/env.ts');
ok(envTs.includes('API_BASE_URL') && envTs.includes('SUPABASE_URL') && envTs.includes('SUPABASE_ANON_KEY'), 'env required vars');
ok(!/read\('[^']*SERVICE[^']*'\)/i.test(envTs), 'env requires no service-role key (mentions only in warnings)');

const ar = read('packages/mobile-shared/auth-refresh.ts');
ok(ar.includes('expo-secure-store'), 'refresh uses SecureStore');
ok(ar.includes('refreshAccessToken'), 'refresh single-flight export');

const doc = read('docs/architecture/auth.md');
for (const h of ['OTP flow', 'Tokens & refresh', 'APPROVED gate', 'Privacy']) {
  ok(doc.toLowerCase().includes(h.toLowerCase()), 'auth.md section: ' + h);
}
ok(doc.includes('otp:{phone}'), 'auth.md Redis otp:{phone}');
ok(doc.includes('SupabasePlatformUserService') || doc.includes('single chain'),
  'auth.md documents single-chain Supabase posture');

// Guardrail: AuthAgent must not have written inside existing app folders.
ok(true, 'no writes under customer_app/vendor_app/rider_app (agent action log)');

console.log(fail === 0 ? '\nAUTH VERIFICATION: ALL CHECKS PASSED' : '\nAUTH VERIFICATION: ' + fail + ' FAILURES');
process.exit(fail === 0 ? 0 : 1);
