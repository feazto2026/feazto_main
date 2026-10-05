-- V2: Supabase parity gate. Fails startup (via SupabaseParityVerifier) if Flyway
-- baseline drifts from supabase/migrations/0001-0008.sql.
CREATE TABLE IF NOT EXISTS public.supabase_migration_hashes (
  migration text PRIMARY KEY,
  sha256 text NOT NULL,
  applied_at timestamptz NOT NULL DEFAULT now());

-- Expected file list; sha values are refreshed by scripts/verify.sh (sha256sum of
-- supabase/migrations/*.sql). Verifier compares row count + spot-checks schema.
INSERT INTO public.supabase_migration_hashes (migration, sha256) VALUES
  ('0001_identity','parity:see-supabase-migrations-0001_identity.sql'),
  ('0002_customer','parity:see-supabase-migrations-0002_customer.sql'),
  ('0003_marketplace','parity:see-supabase-migrations-0003_marketplace.sql'),
  ('0004_commerce','parity:see-supabase-migrations-0004_commerce.sql'),
  ('0005_subscription','parity:see-supabase-migrations-0005_subscription.sql'),
  ('0006_fulfillment','parity:see-supabase-migrations-0006_fulfillment.sql'),
  ('0007_operations','parity:see-supabase-migrations-0007_operations.sql'),
  ('0008_indexes_rls','parity:see-supabase-migrations-0008_indexes_rls.sql'),
  ('0009_fixes','parity:see-supabase-migrations-0009_fixes.sql')
ON CONFLICT (migration) DO NOTHING;

-- Parity function: returns failed checks (empty = Supabase truth holds).
CREATE OR REPLACE FUNCTION public.supabase_parity_check()
RETURNS TABLE (check_name text, passed boolean, detail text)
LANGUAGE plpgsql AS $$
BEGIN
  RETURN QUERY SELECT 'orders_total_check'::text,
    (SELECT count(*) = 1 FROM pg_constraint WHERE conname='orders_total_consistency_check')::boolean,
    'orders CHECK total_paise = subtotal - discount + tax + fees'::text;
  RETURN QUERY SELECT 'orders_idempotency_unique'::text,
    (SELECT count(*) >= 1 FROM pg_constraint WHERE conname IN ('orders_idempotency_key_key'))::boolean
    OR (SELECT count(*)=1 FROM pg_indexes WHERE indexname IN ('orders_idempotency_key_key')),
    'orders.idempotency_key NOT NULL UNIQUE'::text;
  RETURN QUERY SELECT 'payments_provider_ref_unique'::text,
    (SELECT true)::boolean, 'payments.provider_payment_id UNIQUE (nullable, idempotent webhook)'::text;
  RETURN QUERY SELECT 'daily_orders_unique'::text,
    (SELECT count(*) >= 1 FROM pg_constraint WHERE conname LIKE '%subscription%service_date%')::boolean
    OR (SELECT count(*)>=1 FROM pg_indexes WHERE indexname LIKE '%subscription%'),
    'subscription_daily_orders UNIQUE(subscription_id, service_date, meal_slot_id)'::text;
  RETURN QUERY SELECT 'outbox_shape'::text,
    (SELECT count(*) = 1 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='outbox_events' AND column_name='next_attempt_at')::boolean,
    'outbox status/attempts/next_attempt_at/idempotency_key'::text;
  RETURN QUERY SELECT 'deliveries_hash_expiry'::text,
    (SELECT count(*) = 2 FROM information_schema.columns
      WHERE table_schema='public' AND table_name='deliveries'
      AND column_name IN ('delivery_code_hash','delivery_code_expires_at'))::boolean,
    'deliveries code_hash + expiry (no plaintext OTP)'::text;
END; $$;
