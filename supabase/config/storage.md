# Storage — Codewild Food Platform

Source of truth: buckets are created in `supabase/migrations/0008_indexes_rls.sql`
and hardened (flag repair + policy re-assert) in `supabase/migrations/0009_fixes.sql`.
Rule: **clients never write to Storage directly** — uploads go through the
Spring Boot backend (validates type/size/ownership, writes with `service_role`
or mints a short-lived signed upload URL). Private documents are served via
signed URLs or backend-proxied downloads only.

## Buckets

| Bucket | Public | Contents | Served to |
|---|---|---|---|
| `vendor-profile-images` | ✅ | Kitchen / profile photos | Anyone (read) |
| `menu-images` | ✅ | Dish photos | Anyone (read) |
| `customer-avatars` | ✅ | Customer profile photos | Anyone (read) |
| `vendor-documents` | 🔒 private | KYC, FSSAI, bank proofs (`vendor_verifications.document_urls`) | Backend-signed URL only |
| `rider-documents` | 🔒 private | Rider KYC (`rider_documents.file_url`) | Backend-signed URL only |
| `delivery-proofs` | 🔒 private | Proof-of-delivery media (`deliveries.proof_of_delivery`) | Backend-signed URL only |
| `support-attachments` | 🔒 private | Ticket attachments (`support_ticket_messages.attachments`) | Backend-signed URL only |

## Storage policies (`storage.objects`)

- `public_bucket_read` — `SELECT` for `anon, authenticated` where
  `bucket_id IN ('vendor-profile-images','menu-images','customer-avatars')`.
- **No** `INSERT` / `UPDATE` / `DELETE` policies exist for `anon` /
  `authenticated` on any bucket ⇒ client writes are denied by default.
- `service_role` bypasses RLS ⇒ backend reads/writes all buckets.

## Path conventions (enforced backend-side)

```text
vendor-profile-images/{vendor_id}/profile.jpg
menu-images/{vendor_id}/{menu_item_id}.jpg
customer-avatars/{customer_id}/avatar.jpg
vendor-documents/demo/...            # seed placeholders only
vendor-documents/{vendor_id}/{verification_id}/{filename}
rider-documents/{rider_id}/{document_id}/{filename}
delivery-proofs/{delivery_id}/{filename}
support-attachments/{ticket_id}/{filename}
```

Max upload sizes and MIME allow-lists (jpg/png/webp/pdf) are enforced in the
backend upload endpoint, not in SQL — see API docs when the backend lands.

## Private-document access checklist

- [ ] Never store a public URL for KYC / payout / proof objects in any table.
- [ ] Serve private objects with `createSignedUrl(..., expiresIn: 300)` or proxy.
- [ ] Log private-document access in `audit_logs` for admin views.
- [ ] Seed data uses `vendor-documents/demo/...` placeholder paths only.
