-- ============================================================================
-- Codewild Food Platform — Demo seed (Chennai + Coimbatore)
-- ============================================================================
-- Usage (local Supabase):
--   supabase db reset              -- applies migrations + this seed, OR
--   psql "$DATABASE_URL" -f supabase/seed/demo.sql
--
-- Remote (staging only, never production):
--   supabase db push && psql <staging-db-url> -f supabase/seed/demo.sql
--
-- Guarantees:
--   * All IDs are FIXED UUIDs so the script is re-runnable.
--   * Every INSERT uses ON CONFLICT DO NOTHING (or * DO UPDATE for price
--     corrections), so re-seeding never duplicates or destroys data.
--   * Demo phones use the reserved +91 90000 xxxxx range; OTPs are NOT seeded
--     (auth stays in Supabase Auth / OTP provider).
-- Story: "Anbu Kongu Kitchen" — a Coimbatore-style home kitchen operating in
-- Chennai, serving a demo customer who relocated Chennai <- Coimbatore.
-- Money in paise. Run AFTER all 0001..0009 migrations.
-- ============================================================================

-- ---- Fixed demo IDs ------------------------------------------------------------
-- platform
SELECT 'demo seed: fixed ids' AS step
WHERE false; -- no-op keeps psql -v quiet-friendly

-- ============================ REGIONS ============================================
INSERT INTO public.regions (id, code, name, kind, state, district, city, parent_id) VALUES
  ('11111111-1111-1111-1111-111111111111', 'TN',        'Tamil Nadu', 'STATE',          'Tamil Nadu', NULL,        NULL,        NULL),
  ('11111111-1111-1111-1111-111111111112', 'CHENNAI',   'Chennai',    'CITY',           'Tamil Nadu', 'Chennai',   'Chennai',   '11111111-1111-1111-1111-111111111111'),
  ('11111111-1111-1111-1111-111111111113', 'COIMBATORE','Coimbatore', 'CITY',           'Tamil Nadu', 'Coimbatore','Coimbatore','11111111-1111-1111-1111-111111111111'),
  ('11111111-1111-1111-1111-111111111114', 'KONGU_NADU','Kongu Nadu', 'CULTURAL_REGION','Tamil Nadu', 'Coimbatore', NULL,       '11111111-1111-1111-1111-111111111111'),
  ('11111111-1111-1111-1111-111111111115', 'CHETTINAD', 'Chettinad',  'CULTURAL_REGION','Tamil Nadu', 'Sivaganga',  NULL,       '11111111-1111-1111-1111-111111111111')
ON CONFLICT (id) DO NOTHING;

-- ============================ CUISINES ============================================
INSERT INTO public.cuisines (id, code, name, category, description) VALUES
  ('22222222-2222-2222-2222-222222222221', 'KONGU',     'Kongu / Coimbatore-style', 'REGIONAL', 'Kongu Nadu home food: kambu, ragi, country chicken, arisi paruppu'),
  ('22222222-2222-2222-2222-222222222222', 'CHETTINAD', 'Chettinad',                'REGIONAL', 'Chettinad home-style classics'),
  ('22222222-2222-2222-2222-222222222223', 'TAMIL_MEALS', 'Tamil Meals',            'MEAL_TYPE','Everyday Tamil home meals (veg / non-veg)'),
  ('22222222-2222-2222-2222-222222222224', 'TIFFIN',    'Tiffin / Breakfast',       'MEAL_TYPE','Idli, dosai, pongal and morning tiffin')
ON CONFLICT (id) DO NOTHING;

-- ============================ SLOTS ================================================
INSERT INTO public.slots (id, code, name, starts_at, ends_at, cutoff_minutes_before, is_active) VALUES
  ('33333333-3333-3333-3333-333333333331', 'BREAKFAST', 'Breakfast', '06:30', '10:30',  60, true),
  ('33333333-3333-3333-3333-333333333332', 'LUNCH',     'Lunch',     '11:30', '15:00',  90, true),
  ('33333333-3333-3333-3333-333333333333', 'SNACKS',    'Snacks',    '15:30', '18:30',  60, true),
  ('33333333-3333-3333-3333-333333333334', 'DINNER',    'Dinner',     '18:30', '22:00',  90, true)
ON CONFLICT (id) DO NOTHING;

-- ========================= SERVICE ZONES (Chennai) ==================================
INSERT INTO public.service_zones
  (id, code, name, city, state, kind, postal_codes, center_lat, center_lng, radius_km, delivery_fee_paise, min_order_paise, is_active)
VALUES
  ('44444444-4444-4444-4444-444444444441', 'CHN_ANNA_NAGAR', 'Anna Nagar', 'Chennai', 'Tamil Nadu', 'AREA', '{600040,600101,600102}', 13.0827, 80.2108, 4.00, 2500, 14900, true),
  ('44444444-4444-4444-4444-444444444442', 'CHN_ADYAR',     'Adyar',      'Chennai', 'Tamil Nadu', 'AREA', '{600020,600041}',         12.9987, 80.2559, 4.00, 2900, 14900, true),
  ('44444444-4444-4444-4444-444444444443', 'CHN_VELACHERY', 'Velachery',  'Chennai', 'Tamil Nadu', 'AREA', '{600042}',                12.9757, 80.2200, 3.50, 2900, 14900, true),
  ('44444444-4444-4444-4444-444444444444', 'CHN_T_NAGAR',   'T. Nagar',   'Chennai', 'Tamil Nadu', 'AREA', '{600017,600088}',         13.0418, 80.2341, 3.50, 2500, 14900, true)
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO USERS (platform level) ===================================
-- auth_user_id left NULL: link to real Supabase Auth users in dev via backend.
INSERT INTO public.platform_users (id, auth_user_id, phone, email, display_name, primary_role_id, account_status, is_phone_verified) VALUES
  ('55555555-5555-5555-5555-555555555551', NULL, '+919000000101', 'demo.customer@codewild.example', 'Demo Customer (Coimbatore -> Chennai)',
    (SELECT id FROM public.roles WHERE code = 'CUSTOMER'), 'ACTIVE', true),
  ('55555555-5555-5555-5555-555555555552', NULL, '+919000000102', 'demo.vendor@codewild.example', 'Anbu — Kongu Kitchen',
    (SELECT id FROM public.roles WHERE code = 'VENDOR'), 'ACTIVE', true),
  ('55555555-5555-5555-5555-555555555553', NULL, '+919000000103', 'demo.rider@codewild.example', 'Demo Rider — Anna Nagar',
    (SELECT id FROM public.roles WHERE code = 'RIDER'), 'ACTIVE', true),
  ('55555555-5555-5555-5555-555555555554', NULL, NULL, 'ops.admin@codewild.example', 'Ops Admin (seed)',
    (SELECT id FROM public.roles WHERE code = 'OPS_ADMIN'), 'ACTIVE', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.user_roles (user_id, role_id) VALUES
  ('55555555-5555-5555-5555-555555555551', (SELECT id FROM public.roles WHERE code = 'CUSTOMER')),
  ('55555555-5555-5555-5555-555555555552', (SELECT id FROM public.roles WHERE code = 'VENDOR')),
  ('55555555-5555-5555-5555-555555555553', (SELECT id FROM public.roles WHERE code = 'RIDER')),
  ('55555555-5555-5555-5555-555555555554', (SELECT id FROM public.roles WHERE code = 'OPS_ADMIN'))
ON CONFLICT DO NOTHING;

-- ===================== DEMO CUSTOMER ================================================
INSERT INTO public.customer_profiles
  (id, user_id, full_name, hometown_region_id, preferred_region_ids, preferred_cuisines, dietary_preferences, preferred_language)
VALUES
  ('66666666-6666-6666-6666-666666666661',
   '55555555-5555-5555-5555-555555555551',
   'Demo Customer',
   '11111111-1111-1111-1111-111111111113',
   '{11111111-1111-1111-1111-111111111114}',
   '{KONGU,TAMIL_MEALS}',
   '{VEG_OPTION}',
   'en')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.addresses
  (id, customer_id, label, address_type, house_flat, street, area, city, state, postal_code,
   latitude, longitude, delivery_instructions, is_default, is_active)
VALUES
  ('66666666-6666-6666-6666-666666666662',
   '66666666-6666-6666-6666-666666666661',
   'Home', 'HOME', '4/22, 3rd Main Road', 'Nolambur Phase II', 'Anna Nagar', 'Chennai',
   'Tamil Nadu', '600101', 13.0720, 80.1950, 'Call on arrival; gate code 4412',
   true, true)
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO VENDOR =====================================================
-- Fixed timestamps (not now()) so re-seed on any day is a deterministic no-op.
INSERT INTO public.vendors
  (id, user_id, kitchen_name, description, phone, email, address_line, area, city, state,
   postal_code, latitude, longitude, native_region_id, cuisine_tags, status,
   commission_bps, rating_avg, rating_count, is_active, service_radius_km,
   preparation_capacity_per_slot, fssai_license_no, payout_account_ref,
   terms_accepted_at, approved_at, approved_by)
VALUES
  ('77777777-7777-7777-7777-777777777771',
   '55555555-5555-5555-5555-555555555552',
   'Anbu Kongu Kitchen',
   'Coimbatore-style home food cooked in Anna Nagar, Chennai. Kongu classics, Tamil meals and tiffin.',
   '+919000000102', 'demo.vendor@codewild.example',
   '12, 6th Street, Anna Nagar', 'Anna Nagar', 'Chennai', 'Tamil Nadu', '600040',
   13.0827, 80.2108,
   '11111111-1111-1111-1111-111111111114',
   '{KONGU,TAMIL_MEALS,TIFFIN}',
   'APPROVED', 1500, 4.60, 128, true, 5.00, 20,
   'FSSAI-DEMO-12401099000123', 'payout_acct_ref_demo_vendor_01',
   TIMESTAMPTZ '2026-09-03 10:00:00+05:30', TIMESTAMPTZ '2026-09-04 10:00:00+05:30',
   '55555555-5555-5555-5555-555555555554')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.vendor_regions (vendor_id, region_id, kind) VALUES
  ('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111114', 'NATIVE'),
  ('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111113', 'SPECIALTY'),
  ('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111112', 'SERVED')
ON CONFLICT DO NOTHING;

INSERT INTO public.vendor_cuisines (vendor_id, cuisine_id) VALUES
  ('77777777-7777-7777-7777-777777777771', '22222222-2222-2222-2222-222222222221'),
  ('77777777-7777-7777-7777-777777777771', '22222222-2222-2222-2222-222222222223'),
  ('77777777-7777-7777-7777-777777777771', '22222222-2222-2222-2222-222222222224')
ON CONFLICT DO NOTHING;

INSERT INTO public.vendor_verifications
  (id, vendor_id, verification_type, status, document_urls, submitted_by, reviewed_by, reviewed_at, admin_notes, idempotency_key)
VALUES
  ('77777777-7777-7777-7777-777777777772',
   '77777777-7777-7777-7777-777777777771', 'KYC', 'APPROVED',
   '{vendor-documents/demo/kyc_aadhaar.pdf}', '55555555-5555-5555-5555-555555555552',
   '55555555-5555-5555-5555-555555555554', TIMESTAMPTZ '2026-09-04 11:00:00+05:30',
   'Seed approval: demo KYC verified.', 'seed-verify-kyc-demo-vendor-01'),
  ('77777777-7777-7777-7777-777777777773',
   '77777777-7777-7777-7777-777777777771', 'FSSAI', 'APPROVED',
   '{vendor-documents/demo/fssai_cert.pdf}', '55555555-5555-5555-5555-555555555552',
   '55555555-5555-5555-5555-555555555554', TIMESTAMPTZ '2026-09-04 11:00:00+05:30',
   'Seed approval: demo FSSAI cert on file.', 'seed-verify-fssai-demo-vendor-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.vendor_service_zones (vendor_id, service_zone_id, custom_delivery_fee_paise, is_active) VALUES
  ('77777777-7777-7777-7777-777777777771', '44444444-4444-4444-4444-444444444441', NULL, true),
  ('77777777-7777-7777-7777-777777777771', '44444444-4444-4444-4444-444444444444', NULL, true)
ON CONFLICT DO NOTHING;

INSERT INTO public.vendor_slots (id, vendor_id, slot_id, service_days, capacity_total, capacity_reserved, is_available) VALUES
  ('77777777-7777-7777-7777-777777777774', '77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333331', '{0,1,2,3,4,5,6}', 30, 0, true),
  ('77777777-7777-7777-7777-777777777775', '77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333332', '{0,1,2,3,4,5,6}', 20, 0, true),
  ('77777777-7777-7777-7777-777777777776', '77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333334', '{0,1,2,3,4,5}',       20, 0, true)
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO MENU ==========================================================
INSERT INTO public.menus (id, vendor_id, name, description, is_active, published_at) VALUES
  ('88888888-8888-8888-8888-888888888881', '77777777-7777-7777-7777-777777777771',
   'Everyday Kongu Menu', 'Lunch meals, tiffin and Kongu specials', true, TIMESTAMPTZ '2026-09-05 10:00:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.menu_categories (id, menu_id, name, sort_order, is_active) VALUES
  ('88888888-8888-8888-8888-888888888882', '88888888-8888-8888-8888-888888888881', 'Kongu Specials', 1, true),
  ('88888888-8888-8888-8888-888888888883', '88888888-8888-8888-8888-888888888881', 'Meals',          2, true),
  ('88888888-8888-8888-8888-888888888884', '88888888-8888-8888-8888-888888888881', 'Tiffin',         3, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.menu_items
  (id, menu_id, category_id, vendor_id, name, description, price_paise, mrp_paise, currency,
   cuisine_tags, region_tags, food_type, meal_types, preparation_time_minutes, is_available, is_active, sort_order)
VALUES
  ('88888888-8888-8888-8888-888888888885', '88888888-8888-8888-8888-888888888881', '88888888-8888-8888-8888-888888888882',
   '77777777-7777-7777-7777-777777777771', 'Kongu Chicken Kuzhambu + Kambu Sadham',
   'Country-chicken kuzhambu, kambu sadham, keerai poriyal, rasam, buttermilk', 18900, 22000, 'INR',
   '{KONGU}', '{KONGU_NADU,COIMBATORE}', 'NON_VEG', '{LUNCH,DINNER}', 45, true, true, 1),
  ('88888888-8888-8888-8888-888888888886', '88888888-8888-8888-8888-888888888881', '88888888-8888-8888-8888-888888888882',
   '77777777-7777-7777-7777-777777777771', 'Arisi Paruppu Sadham (Kongu style)',
   'Signature dal-rice with ghee, appalam, poriyal and kootu', 12900, 14900, 'INR',
   '{KONGU}', '{KONGU_NADU,COIMBATORE}', 'VEG', '{LUNCH}', 30, true, true, 2),
  ('88888888-8888-8888-8888-888888888887', '88888888-8888-8888-8888-888888888881', '88888888-8888-8888-8888-888888888883',
   '77777777-7777-7777-7777-777777777771', 'Tamil Veg Meals (Elai-style)',
   'Sadham, sambar, rasam, kootu, poriyal, appalam, curd, pickle', 11900, 13900, 'INR',
   '{TAMIL_MEALS}', '{CHENNAI}', 'VEG', '{LUNCH}', 25, true, true, 1),
  ('88888888-8888-8888-8888-888888888888', '88888888-8888-8888-8888-888888888881', '88888888-8888-8888-8888-888888888884',
   '77777777-7777-7777-7777-777777777771', 'Kambu Koozh + Vazhakkai Bajji (Tiffin)',
   'Fermented kambu koozh with onion, green chilli and vazhakkai bajji', 7900, 9900, 'INR',
   '{KONGU,TIFFIN}', '{KONGU_NADU}', 'VEG', '{BREAKFAST,SNACKS}', 20, true, true, 1)
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO MEAL PLAN + RIDER ===============================================
INSERT INTO public.meal_plans
  (id, vendor_id, name, description, price_per_meal_paise, price_monthly_paise, currency,
   meals_per_day, trial_available, applicable_days, is_active)
VALUES
  ('99999999-9999-9999-9999-999999999991', '77777777-7777-7777-7777-777777777771',
   'Kongu Monthly Lunch Plan', '22 weekday lunches, Kongu meals on rotation',
   14900, 299900, 'INR', 1, true, '{1,2,3,4,5}', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.riders
  (id, user_id, display_name, phone, vehicle_type, vehicle_number, city, status, kyc_status,
   is_online, max_concurrent_deliveries)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '55555555-5555-5555-5555-555555555553',
   'Demo Rider', '+919000000103', 'BIKE', 'TN-01-DEMO-42', 'Chennai',
   'ACTIVE', 'APPROVED', true, 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rider_availability (rider_id, weekday, start_time, end_time, is_available)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', d, '09:00', '22:00', true
FROM generate_series(0, 6) AS d
ON CONFLICT DO NOTHING;

-- ===================== DEMO DOCUMENTS (private buckets) ============================
-- Paths live in private buckets; served via backend-signed URLs only.
INSERT INTO public.vendor_documents (id, vendor_id, document_type, file_url, status) VALUES
  ('77777777-7777-7777-7777-777777777777', '77777777-7777-7777-7777-777777777771',
   'FSSAI_CERT', 'vendor-documents/demo/fssai_cert.pdf', 'APPROVED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rider_documents (id, rider_id, document_type, file_url, status) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
   'DRIVING_LICENCE', 'rider-documents/demo/dl.pdf', 'APPROVED')
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO COMMERCE CHAIN (coupon -> cart -> order -> pay) ==========
-- Fixed dates (not now()) so re-seed on any day is a no-op (same id + same key).
-- Money in paise; order total satisfies orders_total_consistency_check:
--   total(37700) = 37800 - 5000 + 1900 + 2500 + 500 + 0, subtotal >= discount.
INSERT INTO public.coupons
  (id, code, title, description, discount_type, discount_paise, max_discount_paise,
   min_order_paise, usage_limit_total, usage_limit_per_user, used_count, is_active)
VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'WELCOME50', 'Welcome flat Rs.50 off',
   'Seed demo coupon: flat Rs.50 off above Rs.149', 'FLAT', 5000, 5000,
   14900, 1000, 1, 0, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.carts (id, customer_id, vendor_id, slot_id, address_id, coupon_id, status) VALUES
  ('cccccccc-cccc-cccc-cccc-ccccccccccc1', '66666666-6666-6666-6666-666666666661',
   '77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333332',
   '66666666-6666-6666-6666-666666666662', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.cart_items
  (id, cart_id, menu_item_id, quantity, unit_price_paise_snapshot, customization_hash, customizations)
VALUES
  ('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'cccccccc-cccc-cccc-cccc-ccccccccccc1',
   '88888888-8888-8888-8888-888888888885', 2, 18900, '', '{}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- Subscription contract first (orders.subscription_id FK requires it).
INSERT INTO public.subscriptions
  (id, subscription_number, idempotency_key, customer_id, vendor_id, meal_plan_id, address_id,
   status, start_date, end_date, total_meals, meals_consumed, meals_skipped,
   price_snapshot, total_amount_paise, amount_paid_paise, currency, delivery_preferences, skip_dates)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff1', 'SUB-DEMO-0001', 'seed-sub-demo-01',
   '66666666-6666-6666-6666-666666666661', '77777777-7777-7777-7777-777777777771',
   '99999999-9999-9999-9999-999999999991', '66666666-6666-6666-6666-666666666662',
   'ACTIVE', DATE '2026-10-01', DATE '2026-10-22', 22, 0, 0,
   '{"plan":"Kongu Monthly Lunch Plan","price_per_meal_paise":14900}'::jsonb,
   299900, 299900, 'INR', '{"slot":"LUNCH"}'::jsonb, '{}')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.subscription_schedules (id, subscription_id, weekday, meal_slot_id, quantity, is_active) VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff2', 'ffffffff-ffff-ffff-ffff-fffffffffff1', 1,
   '33333333-3333-3333-3333-333333333332', 1, true)
ON CONFLICT (id) DO NOTHING;

-- Demo one-time order (also serves as the generated daily order for the sub).
INSERT INTO public.orders
  (id, order_number, idempotency_key, customer_id, vendor_id, address_id, slot_id,
   service_date, subscription_id, subscription_schedule_id, coupon_id,
   status, payment_status,
   subtotal_paise, discount_paise, tax_paise, delivery_fee_paise, platform_fee_paise, packaging_fee_paise,
   total_paise, currency, commission_bps_snapshot, platform_commission_paise_snapshot,
   vendor_payout_paise_snapshot, coupon_code_snapshot,
   delivery_address_snapshot, item_count, customer_note, estimated_ready_at)
VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd01', 'ORD-DEMO-0001', 'seed-order-demo-01',
   '66666666-6666-6666-6666-666666666661', '77777777-7777-7777-7777-777777777771',
   '66666666-6666-6666-6666-666666666662', '33333333-3333-3333-3333-333333333332',
   DATE '2026-10-03', 'ffffffff-ffff-ffff-ffff-fffffffffff1', 'ffffffff-ffff-ffff-ffff-fffffffffff2',
   'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
   'PLACED', 'SUCCESS',
   37800, 5000, 1900, 2500, 500, 0,
   37700, 'INR', 1500, 5670,
   32130, 'WELCOME50',
   '{"city":"Chennai","postal_code":"600101","area":"Anna Nagar","label":"Home"}'::jsonb,
   2, 'Seed demo order: lunch for 2026-10-03', TIMESTAMPTZ '2026-10-03 12:30:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.order_items
  (id, order_id, menu_item_id, vendor_id, item_name_snapshot, item_description_snapshot,
   unit_price_paise_snapshot, mrp_paise_snapshot, quantity,
   discount_paise_snapshot, tax_paise_snapshot, line_total_paise,
   cuisine_tags_snapshot, customization_snapshot)
VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd02', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
   '88888888-8888-8888-8888-888888888885', '77777777-7777-7777-7777-777777777771',
   'Kongu Chicken Kuzhambu + Kambu Sadham', 'Country-chicken kuzhambu, kambu sadham, keerai poriyal, rasam, buttermilk',
   18900, 22000, 2,
   0, 0, 37800,
   '{KONGU}', '{}'::jsonb)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.order_status_history
  (id, order_id, from_status, to_status, changed_by, actor_role, change_reason, idempotency_key)
VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd03', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
   'CREATED', 'PLACED', '55555555-5555-5555-5555-555555555554', 'OPS_ADMIN',
   'Seed: demo order placed after server-side payment verify.', 'seed-order-history-demo-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.coupon_usages (id, coupon_id, order_id, customer_id, discount_paise) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb7', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1',
   'dddddddd-dddd-dddd-dddd-dddddddddd01', '66666666-6666-6666-6666-666666666661', 5000)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.payments
  (id, order_id, subscription_id, customer_id, provider, provider_payment_id, provider_order_id,
   idempotency_key, amount_paise, currency, method, status, verified_at)
VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 'dddddddd-dddd-dddd-dddd-dddddddddd01', NULL,
   '66666666-6666-6666-6666-666666666661', 'RAZORPAY', 'pay_demo_01', 'order_demo_01',
   'seed-payment-demo-01', 37700, 'INR', 'UPI', 'SUCCESS', TIMESTAMPTZ '2026-10-03 11:00:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.payment_attempts
  (id, payment_id, attempt_no, provider_reference, status, request_payload, response_payload, idempotency_key)
VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1', 1,
   'pay_demo_01_attempt1', 'SUCCESS', '{"order_id":"dddddddd-dddd-dddd-dddd-dddddddddd01"}'::jsonb,
   '{"provider":"RAZORPAY","status":"captured"}'::jsonb, 'seed-payment-attempt-demo-01')
ON CONFLICT (id) DO NOTHING;

-- No refund for the happy-path demo order (refunds table query path is covered
-- by schema + verification queries; inserting a refund would flip order state).

-- ===================== DEMO SUBSCRIPTION GENERATION LOG ============================
INSERT INTO public.subscription_daily_orders
  (id, subscription_id, order_id, service_date, meal_slot_id, status, idempotency_key, generated_at)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff3', 'ffffffff-ffff-ffff-ffff-fffffffffff1',
   'dddddddd-dddd-dddd-dddd-dddddddddd01', DATE '2026-10-03',
   '33333333-3333-3333-3333-333333333332', 'GENERATED', 'seed-daily-demo-01',
   TIMESTAMPTZ '2026-10-03 08:00:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.subscription_status_history
  (id, subscription_id, from_status, to_status, changed_by, actor_role, change_reason, idempotency_key)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff7', 'ffffffff-ffff-ffff-ffff-fffffffffff1',
   'DRAFT', 'ACTIVE', '55555555-5555-5555-5555-555555555554', 'OPS_ADMIN',
   'Seed: demo subscription activated.', 'seed-sub-history-demo-01')
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO FULFILLMENT (delivery -> assignment -> history) ========
INSERT INTO public.deliveries
  (id, delivery_number, order_id, rider_id, idempotency_key, status,
   pickup_code_hash, pickup_code_expires_at, delivery_code_hash, delivery_code_expires_at,
   pickup_address_snapshot, dropoff_address_snapshot,
   delivery_fee_paise_snapshot, rider_payout_paise_snapshot, tip_paise,
   proof_of_delivery, estimated_pickup_at, estimated_delivery_at)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'DEL-DEMO-0001',
   'dddddddd-dddd-dddd-dddd-dddddddddd01', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
   'seed-delivery-demo-01', 'ASSIGNED',
   'sha256:demo-pickup-hash-placeholder', TIMESTAMPTZ '2026-10-03 13:30:00+05:30',
   'sha256:demo-delivery-hash-placeholder', TIMESTAMPTZ '2026-10-03 14:30:00+05:30',
   '{"area":"Anna Nagar","city":"Chennai"}'::jsonb,
   '{"area":"Anna Nagar","city":"Chennai","postal_code":"600101"}'::jsonb,
   2500, 1500, 0,
   '{}'::jsonb, TIMESTAMPTZ '2026-10-03 12:45:00+05:30', TIMESTAMPTZ '2026-10-03 13:30:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.delivery_assignments
  (id, delivery_id, rider_id, attempt_no, status, assignment_reason, idempotency_key)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 1, 'ACCEPTED',
   '{"zone":"CHN_ANNA_NAGAR","distance_km":1.2,"strategy":"nearest-online"}'::jsonb,
   'seed-assign-demo-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.delivery_status_history
  (id, delivery_id, from_status, to_status, changed_by, actor_role, change_reason, location_snapshot, idempotency_key)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
   'AVAILABLE', 'ASSIGNED', '55555555-5555-5555-5555-555555555554', 'OPS_ADMIN',
   'Seed: dispatch offered to Anna Nagar rider.', '{"lat":13.0827,"lng":80.2108}'::jsonb,
   'seed-delivery-history-demo-01')
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO FINANCE (commission -> payouts -> items) ===============
-- Payout nets satisfy 0009 net-consistency CHECKs:
--   vendor net(32130) = 32130 - 0 - 0 + 0; rider net(1500) = 1500 + 0.
INSERT INTO public.commissions
  (id, order_id, vendor_id, basis_amount_paise, commission_bps_snapshot,
   commission_amount_paise, tax_on_commission_paise, net_vendor_share_paise, currency, status)
VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
   '77777777-7777-7777-7777-777777777771', 37800, 1500,
   5670, 0, 32130, 'INR', 'ACCRUED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.vendor_payouts
  (id, payout_number, vendor_id, period_start, period_end,
   gross_amount_paise, commission_deducted_paise, refunds_deducted_paise, adjustments_paise,
   net_amount_paise, currency, status, idempotency_key, initiated_by)
VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', 'POUT-V-DEMO-01',
   '77777777-7777-7777-7777-777777777771', DATE '2026-09-22', DATE '2026-09-28',
   32130, 0, 0, 0,
   32130, 'INR', 'SCHEDULED', 'seed-vpayout-demo-01', '55555555-5555-5555-5555-555555555554')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.vendor_payout_items (id, payout_id, order_id, commission_id, amount_paise) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3',
   'dddddddd-dddd-dddd-dddd-dddddddddd01', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 32130)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rider_payouts
  (id, payout_number, rider_id, period_start, period_end,
   gross_amount_paise, adjustments_paise, net_amount_paise, currency, status,
   idempotency_key, initiated_by)
VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5', 'POUT-R-DEMO-01',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', DATE '2026-09-22', DATE '2026-09-28',
   1500, 0, 1500, 'INR', 'SCHEDULED',
   'seed-rpayout-demo-01', '55555555-5555-5555-5555-555555555554')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.rider_payout_items (id, payout_id, delivery_id, amount_paise) VALUES
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb6', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 1500)
ON CONFLICT (id) DO NOTHING;

-- ===================== DEMO OPERATIONS (review/ticket/notify/audit/outbox) ========
INSERT INTO public.reviews
  (id, order_id, customer_id, vendor_id, rider_id, delivery_id,
   vendor_rating, food_rating, delivery_rating, comment, status)
VALUES
  ('cccccccc-cccc-cccc-cccc-ccccccccccc3', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
   '66666666-6666-6666-6666-666666666661', '77777777-7777-7777-7777-777777777771',
   'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
   5, 5, 5, 'Seed review: authentic Kongu taste, delivered hot!', 'PUBLISHED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.support_tickets
  (id, ticket_number, requester_id, requester_role, order_id, delivery_id,
   subject, description, category, priority, status)
VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd04', 'TKT-DEMO-0001',
   '55555555-5555-5555-5555-555555555551', 'CUSTOMER',
   'dddddddd-dddd-dddd-dddd-dddddddddd01', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
   'Seed: where is my demo order?', 'Demo ticket proving support query path (order + delivery linked).',
   'ORDER', 'MEDIUM', 'OPEN')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.support_ticket_messages (id, ticket_id, sender_id, message, is_internal) VALUES
  ('dddddddd-dddd-dddd-dddd-dddddddddd05', 'dddddddd-dddd-dddd-dddd-dddddddddd04',
   '55555555-5555-5555-5555-555555555551', 'Seed message: demo ticket created.', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.notifications
  (id, recipient_user_id, channel, template_code, title, body, data, status, dedupe_key, sent_at)
VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee3', '55555555-5555-5555-5555-555555555551',
   'IN_APP', 'ORDER_PLACED', 'Demo order placed', 'Your demo order ORD-DEMO-0001 is confirmed.',
   '{"order_id":"dddddddd-dddd-dddd-dddd-dddddddddd01"}'::jsonb, 'SENT',
   'seed-notif-demo-01', TIMESTAMPTZ '2026-10-03 11:01:00+05:30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.notification_preferences (user_id, push_enabled, sms_enabled, email_enabled, locale) VALUES
  ('55555555-5555-5555-5555-555555555551', true, true, true, 'en')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO public.audit_logs
  (id, actor_id, actor_role, action, entity_type, entity_id, new_value, reason)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff4', '55555555-5555-5555-5555-555555555554', 'OPS_ADMIN',
   'VENDOR_APPROVE', 'vendors', '77777777-7777-7777-7777-777777777771',
   '{"status":"APPROVED","kitchen":"Anbu Kongu Kitchen"}'::jsonb, 'Seed approval audit.')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.outbox_events
  (id, aggregate_type, aggregate_id, event_type, payload, status, idempotency_key)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff5', 'orders', 'dddddddd-dddd-dddd-dddd-dddddddddd01',
   'OrderPlaced', '{"order_number":"ORD-DEMO-0001","total_paise":37700}'::jsonb,
   'PENDING', 'seed-outbox-demo-01')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.idempotency_keys
  (id, scope, key, user_id, status, response_code, response_body)
VALUES
  ('ffffffff-ffff-ffff-ffff-fffffffffff6', 'orders', 'seed-order-demo-01',
   '55555555-5555-5555-5555-555555555551', 'COMPLETED', 201,
   '{"order_id":"dddddddd-dddd-dddd-dddd-dddddddddd01"}'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ---- Sanity output (covers every domain; run twice must print identical counts) --
DO $$
DECLARE
  c_regions int; c_cuisines int; c_zones int; c_slots int; c_items int;
  c_orders int; c_payments int; c_deliveries int; c_subs int; c_daily int;
  c_payouts int; c_reviews int; c_tickets int; c_notifs int; c_outbox int; c_idem int;
BEGIN
  SELECT count(*) INTO c_regions FROM public.regions;
  SELECT count(*) INTO c_cuisines FROM public.cuisines;
  SELECT count(*) INTO c_zones FROM public.service_zones;
  SELECT count(*) INTO c_slots FROM public.slots;
  SELECT count(*) INTO c_items FROM public.menu_items;
  SELECT count(*) INTO c_orders FROM public.orders;
  SELECT count(*) INTO c_payments FROM public.payments;
  SELECT count(*) INTO c_deliveries FROM public.deliveries;
  SELECT count(*) INTO c_subs FROM public.subscriptions;
  SELECT count(*) INTO c_daily FROM public.subscription_daily_orders;
  SELECT count(*) INTO c_payouts FROM public.vendor_payouts;
  SELECT count(*) INTO c_reviews FROM public.reviews;
  SELECT count(*) INTO c_tickets FROM public.support_tickets;
  SELECT count(*) INTO c_notifs FROM public.notifications;
  SELECT count(*) INTO c_outbox FROM public.outbox_events;
  SELECT count(*) INTO c_idem FROM public.idempotency_keys;
  RAISE NOTICE 'demo seed ok: regions=%, cuisines=%, zones=%, slots=%, items=%, orders=%, payments=%, deliveries=%, subs=%, daily=%, payouts=%, reviews=%, tickets=%, notifs=%, outbox=%, idem=%',
    c_regions, c_cuisines, c_zones, c_slots, c_items,
    c_orders, c_payments, c_deliveries, c_subs, c_daily,
    c_payouts, c_reviews, c_tickets, c_notifs, c_outbox, c_idem;
END;
$$;
