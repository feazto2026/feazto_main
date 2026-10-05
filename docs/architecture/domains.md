# Domain Model & Module Boundaries

> Companion to `overview.md`. Package target:
> `services/api/src/main/java/com/codewild/food/`.

## 1. Bounded contexts (logical modules in ONE deployable)

```
IDENTITY & ACCESS   Auth / User / Roles / Permissions / Sessions
CUSTOMER            Profile / Address / Preferences / Discovery
MARKETPLACE         Vendor / Verification / Cuisine / Menu / Availability / Slots
COMMERCE            Cart / Pricing / Order / Payment / Refund
SUBSCRIPTION        Meal plans / Subscriptions / Daily order generation
FULFILLMENT         Delivery / Rider / Dispatch / Pickup / Proof-of-delivery
FINANCE             Commission / Vendor payout / Rider payout / Refunds ledger
OPERATIONS          Admin / Support / Audit / Service zones / Configuration
NOTIFICATION        Push / SMS / Email / In-app (semantic events in, provider calls out)
SHARED              Security / Events+Outbox / Errors / Idempotency / Audit / Observability
```

Physical shape per module:

```
<module>/{api,application,domain,infrastructure}
api            controllers + request/response DTOs (no business logic)
application    use-cases, orchestration, transaction boundaries
domain         entities, policies, state-transition validators
infrastructure repositories, adapters, provider clients
```

## 2. Ownership matrix (single writer rule)

| Entity | Owner | Others may… |
|--------|-------|-------------|
| User, role grants | Identity | read id/role/status |
| CustomerProfile, Address, preferences | Customer | Fulfillment reads delivery address snapshot |
| VendorProfile, VendorVerification, VendorCuisine, VendorAvailability | Marketplace (+Operations for approval) | Commerce reads snapshot; Finance reads payout details |
| Menu, MenuCategory, MenuItem, availability | Marketplace | Commerce snapshots price/name at order time |
| Cart | Commerce | nobody else writes |
| Order, OrderItem, OrderStatusHistory | Commerce | Fulfillment appends delivery-linked transitions via Commerce service; Finance reads |
| Payment, PaymentAttempt, Refund | Commerce (+Finance ledger views) | nothing writes payment state except Commerce webhook/verify path |
| Subscription, SubscriptionSchedule, GeneratedOrder link | Subscription | Commerce creates the daily `Order` row; Subscription owns idempotency guard |
| Delivery, DispatchAssignment, RiderProfile, RiderAvailability | Fulfillment | Commerce reads delivery status for order timeline |
| Commission, VendorPayout, RiderPayout | Finance | read-only projections of Commerce/Fulfillment facts |
| SupportTicket, AuditLog, ServiceZone, Slot, config | Operations | all modules emit audit via shared helper |
| Notification | Notification | triggered by domain events, never by direct cross-module calls |

Cross-domain mutation goes through the owning **application service** or a
**domain event**, never a foreign repository.

## 3. Core entities (minimum)

```
User → CustomerProfile → Address
User → VendorProfile → {VendorVerification, VendorCuisine, VendorAvailability,
                        VendorDocument, Menu → MenuCategory → MenuItem}
User → RiderProfile → RiderAvailability
Customer → Cart → Order → {OrderItem, Payment(+PaymentAttempt), Delivery,
                          OrderStatusHistory, Review, Refund, Cancellation}
Customer → Subscription → SubscriptionSchedule → GeneratedOrder (→ Order)
Vendor → {Orders(view), MealPlans, VendorPayouts}   Rider → {Deliveries, RiderPayouts}
ServiceZone / Slot / Coupon / Commission / Notification / SupportTicket / AuditLog
```

Conventions: UUID PKs, FKs everywhere, `created_at/updated_at`,
status-history tables for Order/Delivery/Subscription, immutable finance rows,
soft-delete only where history demands it.

## 4. Financial snapshot rule

At order creation persist `item_name_snapshot, unit_price_snapshot, quantity,
discount_snapshot, tax_snapshot, delivery_fee_snapshot, platform_fee_snapshot,
vendor_commission_snapshot`. Reports read snapshots, never today's menu price.

## 5. Region / hometown model (first-class, not hardcode)

```
Region → State → District → City/Town → Cultural/Cuisine region
Cuisine → {regional, community, meal-type, food-category}
Vendor:  native_region, specialty_regions[], cuisine_tags[]
Customer: hometown_region, preferred_regions[], preferred_cuisines[]
MenuItem: cuisine_tags[], region_tags[]
```

These drive **discovery ranking/filters only**. Never `if (city == 'Chennai')`
in business logic; serviceability comes from `ServiceZone` + slot + capacity.

## 6. Service seams for clients (what each app may call)

- **Customer**: own profile/addresses/cart/reviews; read vendors/menus/slots/serviceability; own orders/subscriptions/payments.
- **Vendor**: own profile/menu/availability/capacity/meal-plans; vendor-side order transitions (`accept → preparing → ready`) on own orders only.
- **Rider**: own availability; assignment accept/reject; QR pickup; PoD delivery — only on assigned delivery in valid state.
- **Admin**: everything via permission scopes (see ADR-006); all writes audited with reason.

## 7. Anti-patterns blocked by review

- Generic `controller/service/repository` packages spanning the platform.
- `client sends status=X` endpoints.
- Frontend price/commission/refund maths.
- JSONB blobs replacing relational order/subscription/delivery facts.
- Extracting a microservice "because it has a folder".
