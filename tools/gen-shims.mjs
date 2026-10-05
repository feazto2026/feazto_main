import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = 'D:\\feazto_main';
const sharedImport = "from '../../../packages/mobile-shared/src/index.js'";

const SHIMS = ['auth', 'vendors', 'menus', 'cart', 'orders', 'subscriptions', 'payments', 'deliveries', 'notifications', 'support'];
const FN = {
  auth: 'authApi',
  vendors: 'vendorsApi',
  menus: 'menusApi',
  cart: 'cartApi',
  orders: 'ordersApi',
  subscriptions: 'subscriptionsApi',
  payments: 'paymentsApi',
  deliveries: 'deliveriesApi',
  notifications: 'notificationsApi',
  support: 'supportApi'
};

const APPS = {
  'customer-mobile': {
    legacy: 'customer_app/Feazto_Customer_page',
    role: 'Customer',
    desc: 'discovery → cart → checkout → orders → subscriptions → delivery tracking'
  },
  'vendor-mobile': {
    legacy: 'vendor_app/Feazto_cloud_page',
    role: 'Vendor / Home Cook',
    desc: 'onboarding → verification → menu → availability → order preparation → payouts'
  },
  'rider-mobile': {
    legacy: 'rider_app/FEAzTO-Rider-App',
    role: 'Rider / Delivery Partner',
    desc: 'onboarding → availability → assignment → pickup → delivery proof → earnings'
  }
};

function clientFile(app) {
  return `import { createClient } from '${'../../../packages/mobile-shared/src/index.js'}';
import type { SharedClient } from '${'../../../packages/mobile-shared/src/index.js'}';

// ${app} — shared client singleton. Configure EXPO_PUBLIC_API_BASE_URL in app config.
// Token storage: swap memory default for SecureStore/AsyncStorage in production.
let instance: SharedClient | null = null;

export function getAppClient(): SharedClient {
  if (!instance) {
    const proc: { env?: Record<string, string | undefined> } | undefined =
      (globalThis as { process?: { env?: Record<string, string | undefined> } }).process;
    const baseUrl = proc?.env?.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
    instance = createClient({ baseUrl });
  }
  return instance;
}
`;
}

function shimFile(app, role, name) {
  const fn = FN[name];
  return `// ${role} app — ${name} integration shim.\n// Thin wrapper over packages/mobile-shared. No legacy UI copied here.\n// Legacy screens remain the source of presentation until migrated (see LEGACY_MAP.md).\nimport { ${fn} } ${sharedImport};\nimport { getAppClient } from './_client.js';\n\nexport const ${name} = ${fn}(getAppClient());\nexport default ${name};\n`;
}

function pkgFile(app, role) {
  return JSON.stringify({
    name: `@feazto/${app}`,
    private: true,
    version: '0.1.0',
    type: 'module',
    scripts: { typecheck: 'tsc --noEmit' },
    dependencies: { '@feazto/mobile-shared': 'file:../../packages/mobile-shared' },
    devDependencies: { typescript: '^5.5.3' }
  }, null, 2) + '\n';
}

function readmeFile(app, meta) {
  return `# Feazto ${meta.role} App (canonical)\n\n> Canonical location: \`apps/${app}/\`. Legacy UI lives at \`${meta.legacy}/\` and is **not** copied or deleted here (see root \`LEGACY_MAP.md\`).\n\n## Direction\n\n- Presentation: legacy screens in \`${meta.legacy}/\` stay authoritative until each screen is migrated.\n- Integration: \`api/*.ts\` shims wrap \`packages/mobile-shared\` (envelopes, JWT, Idempotency-Key) against the Spring Boot API (\`/api/v1/...\`).\n- Flow: ${meta.desc}.\n\n## Shims\n\n| file | domain |\n|---|---|\n${SHIMS.map((s) => `| \`api/${s}.ts\` | \`${FN[s]}\` |`).join('\n')}\n\n\`api/_client.ts\` owns the shared client singleton (base URL from \`EXPO_PUBLIC_API_BASE_URL\`, default \`http://localhost:8080\`).\n\n## Use in (legacy or new) screens\n\n\`\`\`ts\nimport { cart } from './api/cart.js';\nconst page = await cart.get();\n\`\`\`\n\n## Migration rule\n\nInspect → map → reuse → refactor → integrate. Delete legacy only when the migrated screen is verified against the real backend workflow.\n`;
}

for (const [app, meta] of Object.entries(APPS)) {
  const dir = join(root, 'apps', app);
  const api = join(dir, 'api');
  mkdirSync(api, { recursive: true });
  writeFileSync(join(dir, 'package.json'), pkgFile(app, meta.role));
  writeFileSync(join(dir, 'README.md'), readmeFile(app, meta));
  writeFileSync(join(api, '_client.ts'), clientFile(app));
  for (const s of SHIMS) writeFileSync(join(api, `${s}.ts`), shimFile(app, meta.role, s));
  writeFileSync(join(dir, 'tsconfig.json'), JSON.stringify({ compilerOptions: { target: 'ES2020', module: 'ESNext', moduleResolution: 'bundler', strict: true, skipLibCheck: true, noEmit: true }, include: ['api'] }, null, 2) + '\n');
}
console.log('done');
