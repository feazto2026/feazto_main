
# CODEWILD TECH PVT LTD
# HOME-MADE FOOD PLATFORM
## MASTER ARCHITECTURE + PRODUCT SPECIFICATION + AI IMPLEMENTATION PROMPT
### Architecture Edition v2.0

> **Document purpose:** This is the source-of-truth architecture and implementation prompt for the Codewild Tech Pvt Ltd home-made food platform.
>
> **Architecture direction:** Build the platform as a **modular monolith with clear domain boundaries**, backed by Supabase PostgreSQL/Auth/Storage and Redis, with an event-driven internal integration model. Keep Customer, Vendor/Home-Cook, Rider and Admin as separate client applications, while making Spring Boot the authoritative business layer.
>
> **Primary principle:** Four applications, one platform, one source of business truth.

---

# 0. EXECUTIVE ARCHITECTURE DECISION

The platform should **not** initially be designed as four independent products or as a distributed microservice system.

It should be designed as:

```text
                         CODEWILD FOOD PLATFORM
                                  │
          ┌───────────────────────┼────────────────────────┐
          │                       │                        │
          ▼                       ▼                        ▼
  Customer Mobile         Vendor/Home-Cook Mobile     Rider Mobile
  React Native            React Native                React Native
          │                       │                        │
          └───────────────────────┬────────────────────────┘
                                  │
                                  ▼
                         Spring Boot API
                         MODULAR MONOLITH
                                  │
             ┌────────────────────┼─────────────────────┐
             │                    │                     │
             ▼                    ▼                     ▼
        Supabase DB            Redis              External Providers
        Auth/Storage        Cache/Locks/Events     Payment/Maps/Push
             │
             ▼
        Admin Web
        React + Vite
```

The Admin Web is also a client of the same backend. It must not connect directly to business tables to perform privileged operations.

### Why this architecture?

The product has strong domain separation, but the early platform benefits from:

- one deployable backend
- one API contract
- transactional consistency
- simpler debugging
- simpler deployment
- simpler local development
- strong security boundaries
- easier AI-assisted development
- ability to extract domains into services later if scale requires it

The architecture must therefore provide **microservice-quality boundaries without prematurely creating microservice operational complexity**.

---

# 1. PRODUCT ARCHITECTURE POV

## 1.1 Product problem

People frequently move away from their hometown for:

- employment
- education
- business
- family reasons
- long-term relocation

After relocation, they may miss familiar regional/home-made food.

At the same time, people living in the destination city may possess the knowledge and skill to prepare food associated with another region.

The platform connects these two sides.

### Example

```text
Customer
Native place: Coimbatore
Current city: Chennai

        │
        │ wants authentic familiar food
        ▼

Codewild Food Platform

        │
        │ discovers
        ▼

Verified Home Cook
Current city: Chennai
Specialty: Coimbatore-style food

        │
        │ prepares
        ▼

Rider
        │
        ▼

Customer
```

This is the central product relationship.

---

# 2. PLATFORM ACTORS

```text
                    PLATFORM
                       │
       ┌───────────────┼────────────────┐
       │               │                │
   CUSTOMER          VENDOR            RIDER
       │           / HOME COOK          │
       │               │                │
       └───────────────┼────────────────┘
                       │
                     ADMIN
```

## Customer

Discovers and purchases home-made food.

## Vendor / Home Cook

Prepares and sells food.

## Rider / Delivery Partner

Moves prepared food from vendor to customer.

## Admin / Operations

Governs and operates the marketplace.

---

# 3. ARCHITECTURAL PRINCIPLES

These principles are mandatory.

## 3.1 One business truth

The Spring Boot backend is the authoritative source for:

- order state
- payment state
- subscription state
- vendor approval
- rider assignment
- pricing
- serviceability
- slot capacity
- commission
- refunds
- permissions

Clients are presentation/interaction layers, not business authorities.

## 3.2 Database is not the API

Clients must not directly perform business mutations against PostgreSQL.

Use:

```text
Client
  ↓
Spring Boot API
  ↓
Domain/Application Services
  ↓
Repository
  ↓
Supabase PostgreSQL
```

## 3.3 Separate domain from transport

Controllers should not contain business logic.

Prefer:

```text
Controller
    ↓
Application Service
    ↓
Domain Service / Policy
    ↓
Repository
```

## 3.4 State transitions are explicit

Important entities must use controlled state machines.

Never allow:

```text
client sends status = COMPLETED
```

to become a valid business operation.

Instead:

```text
POST /orders/{id}/complete
        ↓
OrderApplicationService
        ↓
validate transition
        ↓
persist
        ↓
publish OrderCompleted
```

## 3.5 Events for cross-domain reactions

When a business event occurs, publish an internal event.

Example:

```text
OrderCompleted
    ├── Review eligibility
    ├── Finance
    ├── Vendor earning
    ├── Rider earning
    ├── Notification
    └── Analytics
```

Do not turn every event into a distributed message broker requirement. Start with an internal event abstraction and Redis/outbox-based delivery where appropriate.

## 3.6 Security is server-side

Frontend role checks are for UX only.

Every privileged API must enforce authorization.

## 3.7 Idempotency is mandatory for money and fulfillment

At minimum:

```text
Payment creation
Payment webhook
Order creation
Refund
Subscription generation
Pickup confirmation
Delivery confirmation
```

must be safe against duplicate requests.

---

# 4. C4-STYLE SYSTEM CONTEXT

```text
                         ┌──────────────────────┐
                         │      CUSTOMER        │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │ Customer React Native│
                         └──────────┬───────────┘
                                    │
                                    │ HTTPS
                                    ▼
┌────────────────┐       ┌──────────────────────┐       ┌──────────────────┐
│     VENDOR     │──────▶│                      │◀──────│      RIDER       │
│ React Native   │       │   Spring Boot API    │       │ React Native     │
└────────────────┘       │  Modular Monolith    │       └──────────────────┘
                         │                      │
┌────────────────┐       │ Business Authority   │
│     ADMIN      │──────▶│                      │
│ React + Vite   │       └───────┬───────┬──────┘
└────────────────┘               │       │
                                 │       │
                         ┌───────▼───┐ ┌─▼────────┐
                         │ Supabase  │ │  Redis   │
                         │ DB/Auth/  │ │ Cache/   │
                         │ Storage   │ │ Locks    │
                         └───────────┘ └──────────┘
                                 │
                                 ▼
                         External Providers
                   Payment / Maps / Push / SMS
```

---

# 5. C4 CONTAINER VIEW

The system should be treated as these containers:

```text
┌────────────────────────────────────────────────────────────┐
│                    CODEWILD PLATFORM                       │
│                                                            │
│  Customer App      Vendor App      Rider App     Admin Web  │
│       │                 │              │             │      │
│       └─────────────────┼──────────────┼─────────────┘      │
│                         ▼                                  │
│                 ┌─────────────────┐                        │
│                 │ Spring Boot API │                        │
│                 │                 │                        │
│                 │ Identity        │                        │
│                 │ Marketplace    │                        │
│                 │ Commerce       │                        │
│                 │ Fulfillment    │                        │
│                 │ Subscription   │                        │
│                 │ Finance        │                        │
│                 │ Operations     │                        │
│                 └───────┬─────────┘                        │
│                         │                                  │
│              ┌──────────┴───────────┐                      │
│              ▼                      ▼                      │
│        Supabase              Redis                         │
│        PostgreSQL            Cache/Locks                   │
│        Auth                  Idempotency                   │
│        Storage               Rate Limits                   │
└────────────────────────────────────────────────────────────┘
```

---

# 6. BACKEND DOMAIN ARCHITECTURE

The Spring Boot application should be organized around business capabilities rather than technical layers alone.

Recommended bounded contexts:

```text
┌──────────────────────────────────────────────────────────┐
│ IDENTITY & ACCESS                                         │
│ Auth / User / Roles / Permissions / Sessions             │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ CUSTOMER                                                  │
│ Profile / Address / Preferences / Discovery              │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ MARKETPLACE                                               │
│ Vendor / Home Cook / Cuisine / Menu / Availability       │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ COMMERCE                                                  │
│ Cart / Pricing / Order / Payment / Refund                │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ SUBSCRIPTION                                              │
│ Meal Plans / Subscription / Daily Order Generation      │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ FULFILLMENT                                               │
│ Delivery / Rider / Dispatch / Pickup / Delivery Proof   │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ PLATFORM OPERATIONS                                       │
│ Admin / Support / Audit / Service Zones / Configuration │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ FINANCE                                                   │
│ Commission / Vendor Payout / Rider Payout / Refunds     │
└──────────────────────────────────────────────────────────┘
```

These are logical boundaries inside one Spring Boot application.

---

# 7. SPRING BOOT MODULAR MONOLITH

Recommended structure:

```text
services/api/src/main/java/com/codewild/food/

├── identity/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
│
├── customer/
├── marketplace/
├── commerce/
├── subscription/
├── fulfillment/
├── finance/
├── operations/
├── notification/
│
└── shared/
    ├── security/
    ├── events/
    ├── errors/
    ├── idempotency/
    ├── audit/
    └── observability/
```

Each module should follow:

```text
module/
├── api
├── application
├── domain
└── infrastructure
```

### api

HTTP controllers and request/response DTOs.

### application

Use cases and orchestration.

### domain

Business rules, policies, state transitions and domain objects.

### infrastructure

Persistence, external providers, adapters and technical implementations.

This separation is more important than forcing every class into generic folders such as:

```text
controller/
service/
repository/
```

across the entire application.

---

# 8. DOMAIN OWNERSHIP

Every important entity needs one authoritative owner.

| Entity | Owning Domain |
|---|---|
| User | Identity |
| CustomerProfile | Customer |
| Address | Customer |
| VendorProfile | Marketplace |
| VendorVerification | Marketplace / Operations |
| Menu | Marketplace |
| MenuItem | Marketplace |
| MealPlan | Subscription / Marketplace |
| Cart | Commerce |
| Order | Commerce |
| Payment | Commerce / Finance |
| Subscription | Subscription |
| Delivery | Fulfillment |
| RiderProfile | Fulfillment |
| DispatchAssignment | Fulfillment |
| Commission | Finance |
| VendorPayout | Finance |
| RiderPayout | Finance |
| SupportTicket | Operations |
| AuditLog | Operations |

Other modules may reference another domain's data, but should not casually mutate it.

---

# 9. DATA OWNERSHIP AND CLIENT ACCESS

## Customer

Can mutate:

```text
Own profile
Own addresses
Own preferences
Own cart
Own eligible reviews
```

Can read:

```text
Available vendors
Available menus
Own orders
Own subscriptions
Relevant delivery status
```

## Vendor

Can mutate:

```text
Own profile
Own menu
Own availability
Own capacity
Own meal plans
Eligible order preparation states
```

## Rider

Can mutate:

```text
Own availability
Own accepted delivery actions
Pickup confirmation
Delivery confirmation
```

## Admin

Can perform privileged operations through explicit permission scopes.

No client receives direct database write privileges for business tables.

---

# 10. DATABASE ARCHITECTURE

Use PostgreSQL through Supabase.

Logical schema grouping can be represented by domain prefixes or carefully organized tables.

Core relationship:

```text
User
 ├── CustomerProfile
 │    └── Address
 │
 ├── VendorProfile
 │    ├── VendorVerification
 │    ├── VendorCuisine
 │    ├── VendorAvailability
 │    └── Menu
 │         ├── MenuCategory
 │         └── MenuItem
 │
 └── RiderProfile
      └── RiderAvailability


Customer
   │
   ├── Cart
   │
   ├── Order
   │    ├── OrderItem
   │    ├── Payment
   │    ├── Delivery
   │    ├── OrderStatusHistory
   │    ├── Review
   │    └── Refund
   │
   └── Subscription
        ├── SubscriptionSchedule
        └── GeneratedOrder


Vendor
   ├── Orders
   ├── MealPlans
   └── Payouts

Rider
   ├── Deliveries
   └── Payouts
```

## Database rules

Use:

- UUID primary identifiers
- foreign keys
- unique constraints
- indexes for high-volume queries
- created_at / updated_at
- status history
- soft deletion only where required
- immutable financial transaction records
- transactional constraints for capacity
- unique constraints for idempotency

---

# 11. FINANCIAL DATA MUST BE HISTORICAL

Do not calculate historical reports using today's menu prices.

At order creation, preserve snapshots such as:

```text
item_name_snapshot
unit_price_snapshot
quantity
discount_snapshot
tax_snapshot
delivery_fee_snapshot
platform_fee_snapshot
vendor_commission_snapshot
```

This ensures that an old order remains financially explainable even after:

```text
menu price changes
commission changes
tax changes
vendor changes
```

---

# 12. REDIS ARCHITECTURE

Redis is a supporting infrastructure component, not the system of record.

Use it for:

```text
OTP throttling
Rate limiting
Short-lived tokens/state
Idempotency
Distributed locks
Caching
Temporary dispatch state
Frequently accessed serviceability
Short-lived realtime coordination
```

Do not use Redis as the permanent source of truth for:

```text
orders
payments
customers
vendors
financial transactions
subscriptions
```

Example keys:

```text
rate-limit:user:{id}
otp:{phone}
idempotency:{operation}:{key}
lock:order:{orderId}
lock:slot:{slotId}:{date}
cache:vendor:{vendorId}
cache:serviceability:{zone}:{address}
```

Apply TTLs deliberately.

---

# 13. EVENT ARCHITECTURE

Use domain events to decouple side effects.

Core events:

```text
UserRegistered
VendorSubmitted
VendorApproved
MenuPublished

OrderCreated
PaymentSucceeded
PaymentFailed
OrderAccepted
OrderPreparing
OrderReady
RiderAssigned
OrderPickedUp
OrderOutForDelivery
OrderDelivered
OrderCompleted
OrderCancelled

SubscriptionActivated
SubscriptionPaused
SubscriptionSkipped
SubscriptionCancelled
DailyMealOrderGenerated

RefundCreated
RefundCompleted

VendorPayoutCreated
RiderPayoutCreated
```

Event pattern:

```text
COMMAND
  ↓
Application Service
  ↓
Domain State Change
  ↓
Transaction Commit
  ↓
Domain Event
  ↓
Handlers
```

For important events that must survive process failure, use an **outbox pattern**.

```text
Database Transaction
 ├── business record
 └── outbox event

Background publisher
        ↓
Redis / notification / integrations
```

This prevents:

```text
DB updated successfully
BUT
event was lost because application crashed
```

---

# 14. ORDER STATE MACHINE

The order lifecycle must be explicitly modeled.

```text
                  ┌───────────────┐
                  │    CREATED    │
                  └───────┬───────┘
                          ▼
                 ┌─────────────────┐
                 │ PAYMENT_PENDING │
                 └───────┬─────────┘
                         │ success
                         ▼
                    ┌─────────┐
                    │ PLACED  │
                    └────┬────┘
                         ▼
                ┌─────────────────┐
                │ VENDOR_ACCEPTED │
                └───────┬─────────┘
                        ▼
                  ┌────────────┐
                  │ PREPARING  │
                  └──────┬─────┘
                         ▼
              ┌────────────────────┐
              │ READY_FOR_PICKUP   │
              └─────────┬──────────┘
                        ▼
                ┌───────────────┐
                │ RIDER_ASSIGNED│
                └───────┬───────┘
                        ▼
                  ┌───────────┐
                  │ PICKED_UP │
                  └─────┬─────┘
                        ▼
               ┌─────────────────┐
               │ OUT_FOR_DELIVERY│
               └────────┬────────┘
                        ▼
                  ┌───────────┐
                  │ DELIVERED │
                  └─────┬─────┘
                        ▼
                 ┌────────────┐
                 │ COMPLETED  │
                 └────────────┘
```

Cancellation/refund paths must be modeled separately and validated against the current state.

---

# 15. SUBSCRIPTION ARCHITECTURE

Subscription is not itself a daily delivery.

It is a recurring contract that generates operational orders.

```text
Subscription
      │
      ├── schedule
      ├── vendor
      ├── meal plan
      ├── customer
      └── delivery preferences
              │
              ▼
       Daily Order Generator
              │
              ▼
        Daily Order
              │
              ▼
        Normal Fulfillment
```

This separation is critical.

It allows:

```text
subscription paused
```

without corrupting already-generated orders.

---

# 16. DELIVERY ARCHITECTURE

Delivery is a fulfillment object connected to an order.

```text
Order
  │
  └── Delivery
        │
        ├── Rider
        ├── Assignment
        ├── Pickup
        ├── Route
        └── Delivery confirmation
```

Assignment:

```text
READY_FOR_PICKUP
        ↓
Find eligible riders
        ↓
Create assignment
        ↓
Offer rider
        ↓
Accept / Reject / Timeout
        ↓
Retry
        ↓
Escalate to operations
```

The dispatch strategy should be replaceable.

Initial implementation may use:

```text
service zone
+
availability
+
distance
+
current workload
```

Later it can evolve without rewriting Order.

---

# 17. PAYMENT ARCHITECTURE

Use an adapter interface:

```java
interface PaymentProvider {
    PaymentIntent createPayment(CreatePaymentCommand command);
    PaymentVerification verifyPayment(String paymentReference);
    RefundResult refund(RefundCommand command);
}
```

The domain must not depend directly on one provider's SDK.

Flow:

```text
Customer
   ↓
Spring Boot
   ↓
Payment Provider
   ↓
Payment Webhook
   ↓
Spring Boot verification
   ↓
Payment state
   ↓
Order state
```

Payment success must be confirmed server-side.

---

# 18. SERVICEABILITY ARCHITECTURE

Serviceability must be a domain capability, not a UI filter.

Inputs can include:

```text
Customer address
Vendor location
Vendor delivery zone
Current city
Postal code
Geographic boundary
Slot
Time
Vendor availability
Rider availability
```

API example concept:

```text
POST /api/v1/serviceability/check
```

Response should explain:

```text
serviceable
vendor
slot
estimated delivery
delivery fee
reason if unavailable
```

Never hardcode:

```text
if city == Chennai
```

into business logic.

---

# 19. HOME-TOWN / REGIONAL FOOD MODEL

The differentiating concept must be modeled explicitly.

Suggested taxonomy:

```text
Region
 ├── State
 ├── District
 ├── City/Town
 └── Cultural/Cuisine Region

Cuisine
 ├── Regional
 ├── Community
 ├── Meal Type
 └── Food Category
```

Vendor may have:

```text
native_region
specialty_regions[]
cuisine_tags[]
```

Customer may have:

```text
hometown
preferred_regions[]
preferred_cuisines[]
```

These are preferences and discovery metadata, not assumptions.

---

# 20. ADMIN AS OPERATIONS CONTROL PLANE

The Admin portal should not simply be a CRUD dashboard.

It is the **operations control plane**.

```text
                 ADMIN / OPERATIONS
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
   Marketplace       Fulfillment        Finance
       │                 │                  │
   Vendors            Riders             Payments
   Menus              Orders             Refunds
   Verification       Dispatch           Payouts
       │                 │                  │
       └─────────────────┼──────────────────┘
                         │
                      Support
                         │
                      Audit
```

Admin actions must be:

- permission controlled
- auditable
- reversible where possible
- reason captured for sensitive actions

---

# 21. ADMIN PERMISSION MODEL

Do not use only:

```text
ADMIN = everything
```

Use permission scopes.

Example:

```text
VENDOR_VIEW
VENDOR_APPROVE
VENDOR_SUSPEND

ORDER_VIEW
ORDER_CANCEL
ORDER_REFUND

RIDER_VIEW
RIDER_APPROVE
RIDER_SUSPEND

FINANCE_VIEW
PAYOUT_MANAGE

SUPPORT_VIEW
SUPPORT_ASSIGN

AUDIT_VIEW
SETTINGS_MANAGE
```

Roles can then map to permissions:

```text
SUPER_ADMIN
OPS_ADMIN
FINANCE_ADMIN
SUPPORT_ADMIN
```

---

# 22. CLIENT ARCHITECTURE

Each client remains independent because each actor has different responsibilities.

```text
apps/
├── customer-mobile/
├── vendor-mobile/
├── rider-mobile/
└── admin-web/
```

Do not combine all three mobile apps into one role-switching application unless product requirements later demand it.

They may share:

```text
API contracts
validation
networking
authentication helpers
common primitives
```

but must retain independent feature/navigation boundaries.

---

# 23. FRONTEND ARCHITECTURE

Recommended conceptual structure:

```text
Customer App
├── app/
├── navigation/
├── features/
│   ├── auth/
│   ├── discovery/
│   ├── vendor/
│   ├── cart/
│   ├── checkout/
│   ├── orders/
│   ├── subscriptions/
│   └── profile/
├── shared/
│   ├── ui/
│   ├── api/
│   ├── storage/
│   └── utils/
└── config/
```

Vendor and Rider should follow the same principle.

The existing projects should be adapted rather than blindly replaced.

---

# 24. API CONTRACT ARCHITECTURE

The API should be contract-first enough that four clients can safely evolve independently.

Use:

```text
OpenAPI
    ↓
API specification
    ↓
Generated/shared types where practical
```

Version:

```text
/api/v1
```

Breaking changes should result in a new API version or controlled migration.

Every API mutation should document:

```text
authorization
request
response
validation
business errors
idempotency requirements
```

---

# 25. API RESPONSE AND ERROR STANDARD

Success:

```json
{
  "success": true,
  "data": {},
  "message": "Operation completed",
  "requestId": "..."
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "ORDER_SLOT_FULL",
    "message": "The selected meal slot is full.",
    "details": {}
  },
  "requestId": "..."
}
```

Use stable machine-readable error codes.

---

# 26. REALTIME ARCHITECTURE

Realtime is primarily needed for:

```text
Order tracking
Rider assignment
Vendor order updates
Operational alerts
```

Do not use aggressive polling when event-based updates are practical.

Possible flow:

```text
Spring Boot
    ↓
Domain Event
    ↓
Realtime/Notification Adapter
    ↓
Customer/Vendor/Rider
```

Realtime channels must enforce authorization.

---

# 27. NOTIFICATION ARCHITECTURE

Create a provider abstraction:

```text
NotificationService
        │
        ├── PushProvider
        ├── SmsProvider
        ├── EmailProvider
        └── InAppNotificationRepository
```

Business modules should request notifications using semantic events rather than knowing provider-specific APIs.

Example:

```text
OrderReady
    ↓
NotificationHandler
    ↓
"Your order is ready for pickup"
```

---

# 28. SECURITY ARCHITECTURE

Authentication:

```text
Supabase Auth
      ↓
JWT
      ↓
Spring Security
      ↓
Platform User
      ↓
Role + Permissions
```

Authorization checks:

```text
Authentication
      +
Role
      +
Permission
      +
Resource ownership
      +
Resource state
```

Example:

A Rider may have permission to update a delivery, but only if:

```text
delivery.rider_id == authenticated_user.rider_id
```

and the delivery is in a valid state.

---

# 29. TRUST BOUNDARY

Treat every client as untrusted.

```text
UNTRUSTED
────────────────────────────────
Customer app
Vendor app
Rider app
Admin browser
External webhooks
────────────────────────────────
TRUST BOUNDARY
────────────────────────────────
Spring Boot
────────────────────────────────
Protected systems
Supabase DB
Redis
Payment secrets
Admin operations
```

Admin browser is still untrusted from the backend's perspective.

---

# 30. IDEMPOTENCY ARCHITECTURE

For every mutation that can be retried:

```text
Client
  ↓
Idempotency-Key
  ↓
Spring Boot
  ↓
Redis/database idempotency record
  ↓
Execute once
  ↓
Return same logical result
```

Critical operations:

```text
POST /orders
POST /payments
POST /refunds
POST /subscriptions
POST /deliveries/{id}/pickup
POST /deliveries/{id}/complete
```

---

# 31. OBSERVABILITY ARCHITECTURE

Every request should carry:

```text
requestId
traceId where supported
userId where appropriate
```

Monitor:

```text
API latency
error rate
payment failures
order failures
rider assignment failures
subscription generation failures
Redis failures
database failures
notification failures
```

Important business metrics:

```text
orders created
orders completed
orders cancelled
payment success rate
vendor acceptance rate
rider assignment rate
delivery completion rate
subscription activation
subscription churn
```

---

# 32. FAILURE HANDLING

Design for partial failure.

Example:

```text
Payment succeeds
BUT
order creation response times out
```

The retry must not create another charge/order.

Example:

```text
Vendor marks ready
BUT
notification fails
```

The order must still remain ready.

Example:

```text
Rider accepts
BUT
app crashes
```

The assignment must remain persisted.

Business state must not depend on notification success.

---

# 33. DEPLOYMENT ARCHITECTURE

Separate environments:

```text
LOCAL
  ↓
DEVELOPMENT
  ↓
STAGING
  ↓
PRODUCTION
```

Production conceptual architecture:

```text
                    INTERNET
                       │
                 HTTPS / CDN
                       │
              ┌────────┴────────┐
              │                 │
          Admin Web         Mobile Apps
              │                 │
              └────────┬────────┘
                       ▼
                 API / Gateway
                       │
               Spring Boot API
                       │
          ┌────────────┼────────────┐
          │            │            │
          ▼            ▼            ▼
      Supabase       Redis      External APIs
      PostgreSQL                Payment
      Auth                      Maps
      Storage                   Push/SMS
```

The exact hosting provider can be chosen separately.

The architecture must remain portable.

---

# 34. BACKUP AND RECOVERY

Define:

```text
database backup
restore procedure
migration rollback strategy
Redis recovery strategy
storage backup policy
secret rotation
incident recovery
```

Redis data should be considered reconstructable unless explicitly classified otherwise.

PostgreSQL business records are authoritative and must have a backup/recovery strategy.

---

# 35. ARCHITECTURE DECISION RECORDS

Create an ADR directory:

```text
docs/architecture/adr/
```

At minimum document:

```text
ADR-001 Modular Monolith
ADR-002 Supabase as PostgreSQL/Auth/Storage
ADR-003 Redis responsibilities
ADR-004 Event/outbox strategy
ADR-005 Payment provider abstraction
ADR-006 Role/permission model
ADR-007 Subscription daily-order model
ADR-008 Delivery assignment strategy
ADR-009 API versioning
ADR-010 Direct Supabase access policy
```

Each ADR should explain:

```text
Context
Decision
Alternatives
Consequences
```

---

# 36. ARCHITECTURE EVOLUTION PATH

The platform should be able to evolve:

```text
PHASE 1
Modular Monolith
      ↓
PHASE 2
High-scale modules + async workers
      ↓
PHASE 3
Extract only proven high-load domains
```

Possible future extraction candidates:

```text
Dispatch
Notifications
Search
Payments
Analytics
```

Do not extract a service merely because it has a separate folder.

Extract when there is a measurable operational reason.

---

# 37. REPOSITORY ARCHITECTURE

Recommended:

```text
codewild-food-platform/
│
├── apps/
│   ├── customer-mobile/
│   ├── vendor-mobile/
│   ├── rider-mobile/
│   └── admin-web/
│
├── services/
│   └── api/
│
├── packages/
│   ├── api-contracts/
│   ├── shared-types/
│   └── validation/
│
├── supabase/
│   ├── migrations/
│   ├── seed/
│   └── config/
│
├── infra/
│   ├── docker/
│   ├── redis/
│   └── local/
│
├── docs/
│   ├── architecture/
│   │   └── adr/
│   ├── api/
│   ├── database/
│   ├── workflows/
│   └── operations/
│
├── scripts/
├── .env.example
├── docker-compose.yml
├── README.md
└── MASTER_PROMPT.md
```

---

# 38. ARCHITECTURE QUALITY GATES

Before considering the platform integrated, verify:

### Domain

- No business logic in UI
- Clear domain ownership
- Explicit state machines
- Financial snapshots

### Security

- JWT validation
- RBAC/permissions
- ownership checks
- webhook verification
- secrets protected

### Reliability

- idempotency
- transaction boundaries
- outbox for critical events
- retry strategy
- failure isolation

### Data

- foreign keys
- indexes
- uniqueness
- migrations
- backups

### Operations

- audit logs
- request IDs
- metrics
- health checks
- error codes

### Clients

- shared API contracts
- loading states
- empty states
- error states
- token refresh
- realtime authorization

---

# 39. IMPLEMENTATION ORDER — ARCHITECTURE FIRST

The AI coding agent must work in this order.

```text
01. Repository discovery
02. Architecture baseline
03. Domain model
04. Database migrations
05. Authentication/security
06. API foundation
07. Vendor onboarding
08. Menu/availability
09. Customer discovery
10. Cart/checkout
11. Payment
12. Order state machine
13. Vendor fulfillment
14. Rider dispatch
15. Delivery
16. Realtime/notifications
17. Subscription
18. Finance
19. Admin operations
20. Support/audit
21. Testing
22. Observability
23. Production hardening
```

Do not build every frontend screen first and connect the backend at the end.

---

# 40. AI CODING AGENT — ARCHITECTURE MODE

Before changing code, the agent must produce a short internal implementation plan containing:

```text
1. Existing repository structure
2. Existing app architecture
3. Existing dependencies
4. Existing API/mock data
5. Architecture gaps
6. Migration risks
7. Target architecture
8. Files/modules to change
9. Database changes
10. API changes
11. Tests required
```

Then implement incrementally.

After each major phase:

```text
build
test
lint
verify migrations
verify API contract
```

Do not accumulate hundreds of changes without validation.

---

# 41. AI CODING AGENT — EXISTING CODE RULES

The existing React Native applications are valuable assets.

The agent must:

```text
INSPECT
   ↓
MAP
   ↓
REUSE
   ↓
REFACTOR
   ↓
INTEGRATE
```

Not:

```text
DELETE
   ↓
REWRITE EVERYTHING
```

Existing screens should be preserved when they meet the intended product behavior.

Replace code only when:

- insecure
- architecturally incompatible
- duplicated beyond practical maintenance
- incorrect
- impossible to integrate safely

---

# 42. FINAL SYSTEM OF RECORD RULE

The following hierarchy must be respected:

```text
                 BUSINESS TRUTH
                       │
                  PostgreSQL
                       │
             Spring Boot Domain
                       │
             ┌─────────┴─────────┐
             │                   │
           Redis              Clients
        supporting state      presentation
```

Never reverse this hierarchy.

The mobile applications and Admin Web are consumers and command interfaces for the platform.

---

# 43. MASTER END-TO-END BUSINESS FLOW

```text
Customer
  │
  ├── OTP login
  ├── Profile
  ├── Address
  ├── Hometown preference
  └── Discover food
          │
          ▼
       Vendor
          │
          ├── Verified
          ├── Menu
          ├── Meal slots
          └── Capacity
          │
          ▼
       Customer
          │
          ├── Cart
          ├── Checkout
          └── Payment
          │
          ▼
       Spring Boot
          │
          ├── Validate
          ├── Price
          ├── Create Order
          ├── Payment Verification
          └── Notify
          │
          ▼
       Vendor
          │
          ├── Accept
          ├── Prepare
          └── Ready
          │
          ▼
       Dispatch
          │
          ▼
       Rider
          │
          ├── Accept
          ├── Pickup QR
          ├── Navigate
          └── Delivery verification
          │
          ▼
       Customer
          │
          ├── Delivered
          └── Review
          │
          ▼
       Finance
          │
          ├── Commission
          ├── Vendor payout
          └── Rider payout
          │
          ▼
       Admin
          │
          └── Full operational/audit visibility
```

---

# 44. WHAT "DONE" MEANS

The platform is done only when the four applications participate in the same real business workflow.

A fake flow such as:

```text
Customer screen
   ↓
mock order
   ↓
Vendor screen magically changes
```

is not integration.

Real integration means:

```text
Customer API request
      ↓
Spring Boot
      ↓
PostgreSQL transaction
      ↓
Domain event
      ↓
Vendor API/realtime notification
      ↓
Vendor action
      ↓
Spring Boot
      ↓
Rider assignment
      ↓
Rider action
      ↓
Delivery state
      ↓
Customer update
      ↓
Finance
      ↓
Admin visibility
```

---

# 45. FINAL ARCHITECTURE PRINCIPLE

The platform should be understood as:

> **A regional home-food marketplace and fulfillment platform, not merely a food-delivery UI.**

Its core business graph is:

```text
PERSON
  │
  ├── lives in → LOCATION
  │
  ├── originates from → REGION / HOMETOWN
  │
  └── wants → FOOD EXPERIENCE
                     │
                     ▼
              VERIFIED HOME COOK
                     │
                     ├── prepares → MENU
                     ├── serves → SERVICE ZONE
                     └── offers → MEAL SLOT
                                  │
                                  ▼
                               ORDER
                                  │
                                  ▼
                               RIDER
                                  │
                                  ▼
                              CUSTOMER
```

The architecture must preserve that business graph across:

```text
Customer App
Vendor App
Rider App
Admin Web
Spring Boot
Supabase
Redis
External providers
```

The system should be extensible to:

```text
Madurai food in Chennai
Coimbatore food in Chennai
Tirunelveli food in Bengaluru
Kerala food in Chennai
Andhra food in Bengaluru
Bengali food in Delhi
North Indian home food in Hyderabad
```

without hardcoding city-specific business rules.

---

# 46. EXECUTION COMMAND

Use this after placing the document in the repository:

> **Read `MASTER_PROMPT.md` completely before making changes. Treat it as the architecture and implementation source of truth. First inspect all existing Customer, Vendor/Home-Cook and Rider React Native applications and the Admin React/Vite application. Produce a repository and architecture assessment. Then implement the target architecture as a modular Spring Boot monolith with explicit domain boundaries, Supabase PostgreSQL/Auth/Storage, Redis for supporting infrastructure, secure API contracts, server-authoritative state machines, idempotency, domain events/outbox where required, and separate Customer/Vendor/Rider/Admin clients. Preserve useful existing frontend code. Do not blindly rewrite working applications. Do not put business logic in clients. Do not expose privileged database access to clients. Implement the platform incrementally from identity and domain foundations through vendor onboarding, marketplace discovery, cart, payments, orders, subscriptions, dispatch, delivery, notifications, finance and Admin operations. Maintain migrations, API documentation, ADRs, tests, observability and deployment documentation throughout the implementation. Validate the complete end-to-end scenario before declaring the integration complete.**

---

# 47. ORIGINAL PRODUCT/IMPLEMENTATION SPECIFICATION

The detailed product requirements, workflows, screen requirements, payment flows, vendor/rider/customer requirements, testing requirements and implementation guidance from the original master prompt are retained below as the **functional specification and acceptance layer**.

---

# 1. PRODUCT VISION

Build a multi-sided home-made food delivery platform for people who live away from their hometown and want to access the taste and style of food from their native place.

### Real-world example

A customer originally from **Coimbatore** is working and living in **Chennai**. They miss authentic Coimbatore-style home food.

A person in Chennai may have strong knowledge of Coimbatore cooking and may prepare that food from home for sale.

The platform connects:

**Customer**
→ person who wants authentic hometown-style home food

**Vendor / Home Cook**
→ verified person who prepares home-made food and sells it through the platform

**Rider / Delivery Partner**
→ person who collects prepared food and delivers it to the customer

**Admin**
→ platform operator who verifies vendors, manages the marketplace, handles users/orders/riders, commissions, disputes, operational settings and platform governance.

The product is therefore not simply a normal restaurant delivery application. The central product concept is:

> **Discover and order authentic home-made food associated with your hometown, community, cuisine or preferred food tradition, even when you are living away from home.**

The system must support both:

1. **Normal/on-demand food orders**
2. **Recurring meal subscriptions / meal plans**

---

# 2. PRIMARY ENGINEERING OBJECTIVE

Create one maintainable repository containing:

```text
codewild-food-platform/
├── apps/
│   ├── customer-mobile/       # Existing React Native customer app
│   ├── vendor-mobile/         # Existing React Native vendor/home-cook app
│   ├── rider-mobile/          # Existing React Native rider app
│   └── admin-web/             # React + Vite admin portal
│
├── services/
│   └── api/                   # Spring Boot Java backend
│
├── packages/
│   ├── api-contracts/         # Shared API/OpenAPI/generated types where useful
│   ├── shared-types/          # Shared non-platform-specific types/constants
│   └── validation/            # Shared validation rules where practical
│
├── infra/
│   ├── docker/
│   ├── redis/
│   └── local/
│
├── supabase/
│   ├── migrations/
│   ├── seed/
│   └── config/
│
├── docs/
│   ├── architecture/
│   ├── api/
│   ├── workflows/
│   ├── database/
│   └── operations/
│
├── scripts/
├── .env.example
├── docker-compose.yml
├── README.md
└── MASTER_PROMPT.md
```

If the existing project uses another practical folder structure, preserve it where possible, but the final repository must make the ownership and integration boundaries obvious.

---

# 3. NON-NEGOTIABLE ENGINEERING RULES

## 3.1 Inspect before modifying

Before writing code:

1. Inspect all existing Customer React Native code.
2. Inspect all existing Vendor React Native code.
3. Inspect all existing Rider React Native code.
4. Inspect the existing Admin React/Vite code, if present.
5. Identify:
   - package manager
   - React Native version
   - Expo vs bare React Native
   - navigation library
   - state-management solution
   - API layer
   - authentication implementation
   - local storage
   - styling system
   - reusable components
   - environment configuration
   - existing backend assumptions
   - existing screen routes
   - TODOs and mock data
6. Do not blindly replace working implementation.
7. Refactor only when required for integration, correctness, security, maintainability or consistency.

## 3.2 Backend is the business authority

The mobile/web clients must not implement business-critical decisions independently.

The Spring Boot API is the authoritative business layer for:

- orders
- order status
- payment state
- subscriptions
- vendor approval
- menu availability
- delivery assignment
- rider workflow
- commissions
- refunds
- cancellations
- pricing
- serviceability
- slot availability
- permissions
- audit events

Clients may provide UI state, but the server determines the actual state.

## 3.3 Supabase responsibilities

Use Supabase primarily for:

- PostgreSQL database
- Supabase Auth
- file/object storage where appropriate
- realtime capabilities where appropriate

Do not allow client applications to bypass the Spring Boot business layer for business operations.

If clients access Supabase directly, restrict that access to clearly defined safe cases such as authentication/session handling or explicitly approved storage flows.

## 3.4 Redis responsibilities

Use Redis for:

- OTP/rate-limit support where applicable
- short-lived verification state
- API rate limiting
- caching
- distributed locks
- idempotency keys
- temporary delivery/order coordination state
- frequently accessed serviceability/menu data where appropriate
- pub/sub or event coordination if required

Do not use Redis as the permanent source of truth for orders, payments or customer records.

## 3.5 Security

Never place:

- Supabase service-role keys
- database passwords
- Redis passwords
- payment secret keys
- admin secrets

inside mobile applications or browser code.

Only public client-safe configuration may exist in React Native/Admin environments.

---

# 4. TECHNOLOGY STACK

## Frontends

### Customer
- React Native
- Existing frontend must be preserved and integrated
- Android and iOS readiness
- API-driven architecture

### Vendor/Home Cook
- React Native
- Existing frontend must be preserved and integrated

### Rider
- React Native
- Existing frontend must be preserved and integrated

### Admin
- React
- Vite
- TypeScript preferred
- Responsive desktop-first admin UI

## Backend

- Java
- Spring Boot
- Spring Web
- Spring Security
- Bean Validation
- Spring Data/JPA as appropriate
- PostgreSQL through Supabase
- Redis
- OpenAPI/Swagger
- Flyway or equivalent migration strategy if appropriate

## Infrastructure

- Docker/Docker Compose for local development
- Supabase project for managed PostgreSQL/Auth/Storage
- Redis
- Environment-specific configuration

---

# 5. HIGH-LEVEL ARCHITECTURE

```text
                         ┌─────────────────────────┐
                         │      Admin Web           │
                         │      React + Vite        │
                         └────────────┬────────────┘
                                      │ HTTPS
                                      │
┌──────────────────┐                  ▼
│ Customer Mobile  │────────────┐
│ React Native     │            │
└──────────────────┘            │
                                │
┌──────────────────┐            │       ┌─────────────────────┐
│ Vendor Mobile    │────────────┼──────▶│   Spring Boot API   │
│ React Native     │            │       │ Business / Security │
└──────────────────┘            │       └──────────┬──────────┘
                                │                  │
┌──────────────────┐            │                  ├───────────────┐
│ Rider Mobile     │────────────┘                  │               │
│ React Native     │                               ▼               ▼
└──────────────────┘                       ┌──────────────┐  ┌──────────────┐
                                           │  Supabase    │  │    Redis     │
                                           │ Auth/DB/     │  │ Cache/Locks/ │
                                           │ Storage      │  │ Rate limits  │
                                           └──────────────┘  └──────────────┘
```

External integrations should be hidden behind backend interfaces:

```text
PaymentProvider
NotificationProvider
MapsProvider
OTPProvider
FileStorageProvider
```

This prevents vendor lock-in and makes testing easier.

---

# 6. DOMAIN MODEL

The core domain should include, at minimum:

```text
User
CustomerProfile
VendorProfile
VendorVerification
VendorCuisine
VendorAvailability
VendorAddress
VendorDocument
Menu
MenuCategory
MenuItem
MenuItemAvailability
MealPlan
Subscription
SubscriptionMeal
Order
OrderItem
OrderStatusHistory
Payment
PaymentAttempt
Delivery
RiderProfile
RiderAvailability
DeliveryAssignment
DeliveryStatusHistory
Address
ServiceZone
Slot
Cart
Coupon
Commission
VendorPayout
RiderPayout
Rating
Review
Notification
SupportTicket
Refund
Cancellation
AuditLog
```

Use UUIDs for externally exposed IDs unless an existing database convention requires otherwise.

---

# 7. USER TYPES AND ACCESS CONTROL

Define roles such as:

```text
CUSTOMER
VENDOR
RIDER
ADMIN
SUPER_ADMIN
OPS_ADMIN
SUPPORT_ADMIN
FINANCE_ADMIN
```

A user must not gain permissions merely because the frontend hides a screen.

Every protected API must validate:

1. authenticated identity
2. role
3. resource ownership
4. relevant status
5. action permission

Example:

A Vendor must only be able to modify their own menu.

A Rider must only update deliveries assigned to them.

A Customer must only view their own addresses/orders/payments.

Admin permissions must be server-side.

---

# 8. CUSTOMER JOURNEY

Implement the customer journey represented in the provided workflow diagrams.

## 8.1 Entry

```text
Wants food
    ↓
Enter mobile +91
    ↓
Enter OTP
```

Handle:

- OTP request
- resend
- expiry
- attempt limits
- invalid OTP
- retry
- verified session
- account creation for first-time users

## 8.2 Profile

After authentication:

```text
Set profile and address
```

Capture:

- name
- phone
- email if required
- preferred language if supported
- addresses
- house/flat details
- landmark
- delivery instructions
- hometown/native place
- food preferences
- dietary preferences

Do not make unnecessary profile fields mandatory.

## 8.3 Choose journey

The customer can choose:

```text
Normal / one-time order
OR
Subscription / recurring meals
```

---

# 9. NORMAL ORDER FLOW

Expected flow:

```text
Customer
  ↓
Choose food/vendor
  ↓
Configure meal/order
  ↓
Build cart
  ↓
Pay for order
  ↓
Vendor accepts/prepares
  ↓
Rider assigned
  ↓
Food picked up
  ↓
Order delivered
  ↓
Customer confirms delivery
  ↓
Review chef/vendor and rider
  ↓
Order completed
```

The actual state machine must be server controlled.

Suggested order statuses:

```text
CREATED
PAYMENT_PENDING
PAYMENT_CONFIRMED
PAYMENT_FAILED
PLACED
VENDOR_PENDING
VENDOR_ACCEPTED
PREPARING
READY_FOR_PICKUP
RIDER_ASSIGNED
RIDER_ACCEPTED
PICKED_UP
OUT_FOR_DELIVERY
ARRIVED
DELIVERED
CUSTOMER_CONFIRMED
COMPLETED
CANCEL_REQUESTED
CANCELLED
REFUND_PENDING
REFUNDED
FAILED
```

Do not permit arbitrary client-side status changes.

---

# 10. SUBSCRIPTION / MEAL PLAN FLOW

The platform must support recurring meals.

Example:

```text
Choose subscription
       ↓
Pick chef/vendor
       ↓
Pick meal plan
       ↓
Pick days/time/slot
       ↓
Review subscription
       ↓
Pay subscription
       ↓
Subscription activated
       ↓
Daily meal orders generated
       ↓
Vendor prepares meal
       ↓
Rider delivers meal
       ↓
Track daily meal
```

Subscription statuses:

```text
DRAFT
PAYMENT_PENDING
ACTIVE
PAUSED
SKIPPED
EXPIRED
CANCELLED
PAYMENT_FAILED
```

The backend must not create duplicate daily orders if the generation job runs twice.

Use idempotency/unique constraints such as:

```text
(subscription_id, service_date, meal_slot)
```

where appropriate.

---

# 11. VENDOR / HOME-COOK APP

The term "Vendor" in the technical system means the person/home kitchen selling food through the marketplace.

The UI should communicate the home-made nature of the product rather than making every vendor look like a conventional restaurant.

## Vendor onboarding

Vendor registration should include:

- phone
- OTP
- name
- kitchen/home address
- profile photo
- food/cuisine specialties
- hometown/cuisine association
- menu
- preparation capacity
- available days
- meal slots
- service radius
- bank/payment payout details
- required verification/KYC documents
- food/business compliance fields required by the operating jurisdiction
- terms acceptance

Admin must approve a vendor before the vendor can accept live orders.

Vendor lifecycle:

```text
DRAFT
SUBMITTED
UNDER_REVIEW
ACTION_REQUIRED
APPROVED
SUSPENDED
REJECTED
DEACTIVATED
```

## Vendor app flow

Based on the supplied diagram:

```text
Open app
  ↓
Home feed
  ↓
Vendor menu
  ↓
Validate slot
  ↓
Show live tracking
  ↓
Show meal calendar
```

The vendor should be able to:

- view dashboard
- see today's orders
- accept/reject eligible orders
- view preparation time
- manage menu
- manage item availability
- manage meal slots
- manage capacity
- view subscriptions
- view daily meal calendar
- mark food preparing
- mark food ready
- see rider assignment
- see pickup status
- see delivery status
- view earnings
- view payout history
- respond to support tickets
- receive notifications

---

# 12. RIDER / DELIVERY PARTNER APP

Rider flow:

```text
Accept / prepare
      ↓
Mark ready
      ↓
Pickup via QR
      ↓
Deliver order
```

Implement a robust delivery state machine.

Suggested statuses:

```text
AVAILABLE
ASSIGNMENT_PENDING
ASSIGNED
ACCEPTED
ARRIVED_AT_VENDOR
PICKUP_VERIFICATION_PENDING
PICKED_UP
EN_ROUTE
ARRIVED_AT_CUSTOMER
DELIVERY_VERIFICATION_PENDING
DELIVERED
CANCELLED
FAILED
```

## QR pickup

Where the workflow requires QR pickup:

1. Vendor displays/generates order-specific pickup QR.
2. Rider scans QR.
3. Backend verifies:
   - rider assignment
   - order status
   - QR validity
   - QR expiry
4. Backend records pickup.
5. Order transitions to `PICKED_UP`.
6. Customer receives status update.

Do not trust a client-provided order ID alone for pickup verification.

## Delivery confirmation

Support configurable proof-of-delivery methods:

- customer OTP
- QR
- delivery confirmation button
- photo/signature only if operationally required

The backend must validate the delivery state before completion.

---

# 13. ADMIN PORTAL

Build the Admin portal in React + Vite.

The Admin portal is the operational control center connecting all three mobile applications.

## Admin modules

### Dashboard

Show:

- today's orders
- active subscriptions
- active vendors
- pending vendor approvals
- active riders
- deliveries in progress
- failed orders
- cancellations
- payment failures
- gross order value
- commissions
- vendor payouts
- rider payouts
- support issues

### Vendor management

Admin can:

- view vendors
- search/filter
- approve vendor
- reject vendor
- request changes
- suspend vendor
- reactivate vendor
- inspect documents
- inspect menu
- inspect service zones
- configure commission
- inspect ratings
- inspect order history
- inspect payouts

### Customer management

Admin can:

- search customer
- view profile
- view addresses subject to privacy/security rules
- view order history
- view subscriptions
- view support tickets
- suspend/reactivate account where policy permits

### Rider management

Admin can:

- approve rider
- inspect documents
- activate/deactivate rider
- view availability
- view current assignment
- view delivery history
- inspect performance/operational metrics
- manage rider payouts

### Orders

Provide:

- list
- filters
- status
- vendor
- rider
- customer
- date
- payment state
- delivery state
- order detail
- timeline
- cancellation
- refund workflow
- support actions

### Subscriptions

Admin can:

- view subscriptions
- inspect meal schedules
- pause/cancel according to policy
- inspect generated daily orders
- identify failed generation/payment
- manage subscription exceptions

### Menu management

Admin can inspect:

- categories
- items
- prices
- images
- availability
- meal slots
- vendor capacity
- item status

### Service zones

Support location-based serviceability:

```text
City
Area
PIN/postal code
Radius
Geo boundary
Vendor service area
Delivery zone
```

Do not hardcode Chennai/Coimbatore logic into the application.

The system must support multiple cities.

---

# 14. HOMETOWN / CUISINE DISCOVERY

This is a core differentiator.

Customers should be able to discover food based on concepts such as:

```text
Hometown
Cuisine
Regional style
Food category
Vendor
Meal type
Availability
Distance/serviceability
```

Example:

```text
Customer location: Chennai
Native place: Coimbatore

Search:
"Coimbatore home food"

Results:
Verified home cooks in Chennai
who offer Coimbatore-style food
and deliver to the customer's address.
```

This information must be modeled rather than stored only as UI text.

Suggested fields:

```text
vendor.native_region
vendor.cuisine_regions[]
menu_item.cuisine_tags[]
menu_item.region_tags[]
customer.hometown_region
```

Avoid assuming that a person's hometown determines what food they want. These should be preferences/search filters.

---

# 15. CART

Cart must support:

- vendor-specific cart
- menu items
- quantity
- item customization if supported
- meal slot
- delivery address
- pricing
- taxes/fees
- discounts
- delivery fee
- platform fee if applicable
- total
- coupon
- payment method

If the platform initially permits only one vendor per cart, enforce that rule server-side.

---

# 16. PAYMENT ARCHITECTURE

The supplied workflow includes:

```text
UPI
Card
Net Banking
COD
```

Implement payment through a provider abstraction.

Example:

```java
interface PaymentProvider {
    PaymentOrder createPayment(...);
    PaymentResult verifyPayment(...);
    RefundResult refund(...);
}
```

Do not tightly couple business logic to a single payment provider.

Support:

```text
ONLINE
COD
```

and provider-specific methods such as:

```text
UPI
CARD
NET_BANKING
WALLET
```

Payment lifecycle:

```text
CREATED
INITIATED
PENDING
SUCCESS
FAILED
CANCELLED
REFUND_PENDING
PARTIALLY_REFUNDED
REFUNDED
```

Never trust a frontend "payment success" callback without server-side verification/webhook validation.

Use webhook idempotency.

---

# 17. NOTIFICATION SYSTEM

Create a notification abstraction.

Channels:

```text
Push
SMS
Email
In-app
```

Important events:

### Customer

- OTP
- order placed
- payment result
- vendor accepted
- food preparing
- food ready
- rider assigned
- picked up
- out for delivery
- rider near customer
- delivered
- subscription activated
- subscription meal generated
- subscription payment failure
- cancellation
- refund

### Vendor

- new order
- order cancellation
- subscription meal
- rider assigned
- pickup
- payout
- admin action

### Rider

- new delivery assignment
- assignment cancellation
- pickup reminder
- customer delivery information
- support/admin message

### Admin

- pending vendor verification
- failed payments
- operational exceptions
- support escalations

---

# 18. REAL-TIME STATUS

Use backend-controlled real-time updates where beneficial.

Possible architecture:

```text
Spring Boot
   ↓
event/state update
   ↓
Redis / event mechanism
   ↓
notification/realtime layer
   ↓
Customer/Vendor/Rider clients
```

Supabase Realtime may be used where it genuinely simplifies safe realtime delivery.

Do not expose private records to unauthorized users through realtime subscriptions.

---

# 19. API DESIGN

Use REST APIs initially unless an existing implementation requires otherwise.

Base:

```text
/api/v1
```

Suggested modules:

```text
/api/v1/auth
/api/v1/users
/api/v1/customers
/api/v1/vendors
/api/v1/riders
/api/v1/menus
/api/v1/cart
/api/v1/orders
/api/v1/payments
/api/v1/subscriptions
/api/v1/deliveries
/api/v1/addresses
/api/v1/serviceability
/api/v1/slots
/api/v1/notifications
/api/v1/reviews
/api/v1/support
/api/v1/admin
```

Use consistent response/error structures.

Example:

```json
{
  "success": true,
  "data": {},
  "message": "Order created successfully",
  "requestId": "..."
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "ORDER_NOT_SERVICEABLE",
    "message": "The selected address is outside the vendor delivery area.",
    "details": {}
  },
  "requestId": "..."
}
```

Use HTTP status codes correctly.

---

# 20. AUTHENTICATION

Preferred model:

```text
Client
  ↓
Supabase Auth
  ↓
JWT
  ↓
Spring Boot Security
  ↓
Validate JWT
  ↓
Load platform user/role
  ↓
Authorize request
```

The backend must not assume that the existence of a valid Supabase user means the user is approved as a Vendor/Rider.

Maintain application-specific profile/status.

Example:

```text
auth_user_id
platform_user_id
role
account_status
verification_status
```

---

# 21. DATABASE PRINCIPLES

Use PostgreSQL through Supabase.

Important constraints:

- foreign keys
- unique constraints
- check constraints where appropriate
- indexes
- created_at
- updated_at
- soft deletion where business history requires preservation
- status history for important entities
- audit trail for admin actions

Avoid storing entire business workflows as unstructured JSON.

JSONB is acceptable for genuinely flexible metadata, not as a replacement for relational modeling.

---

# 22. ORDER STATE MACHINE

Create one authoritative state transition service.

Example:

```text
PLACED
  ↓
VENDOR_ACCEPTED
  ↓
PREPARING
  ↓
READY_FOR_PICKUP
  ↓
RIDER_ASSIGNED
  ↓
PICKED_UP
  ↓
OUT_FOR_DELIVERY
  ↓
DELIVERED
  ↓
COMPLETED
```

Invalid transitions must be rejected.

For example:

```text
COMPLETED → PREPARING
```

must never be accepted.

Every transition should create a status-history record.

---

# 23. DELIVERY ASSIGNMENT

Initial assignment strategy can be simple:

1. Find active riders in service zone.
2. Filter by availability.
3. Filter by capacity/current assignments.
4. Rank by distance/operational criteria.
5. Offer assignment.
6. Rider accepts or rejects.
7. Retry another eligible rider.
8. Escalate to operations if no rider is available.

Keep assignment logic behind a service:

```text
DeliveryAssignmentService
```

so it can later evolve into more advanced dispatching.

---

# 24. SLOT AND CAPACITY MANAGEMENT

Home cooks may have limited cooking capacity.

Example:

```text
Lunch: 12:00–14:00
Capacity: 20 meals
```

The backend must prevent overselling.

Use transaction-safe capacity reservation.

Redis distributed locks may be used for high-contention reservation, but the database remains authoritative.

---

# 25. SUBSCRIPTION DAILY ORDER GENERATION

Implement a scheduled backend process.

Example:

```text
Every day
    ↓
Find active subscriptions
    ↓
Determine today's scheduled meal
    ↓
Validate vendor/slot/serviceability
    ↓
Create daily order if absent
    ↓
Attach subscription reference
    ↓
Notify vendor/customer
```

Must be idempotent.

If the process runs twice:

```text
ONE daily order
```

not two.

---

# 26. SEARCH

Start with PostgreSQL search/filtering.

Search by:

- vendor name
- cuisine
- region
- hometown
- menu item
- food category
- meal type
- availability
- location/serviceability

Do not introduce Elasticsearch/OpenSearch unless scale actually requires it.

---

# 27. FILE STORAGE

Use Supabase Storage or another configured object store for:

- vendor profile images
- menu images
- verification documents
- rider documents
- customer profile image if needed
- proof-of-delivery files if implemented

Private documents must use signed URLs or protected backend access.

Do not expose KYC documents through public buckets.

---

# 28. ADMIN AUDIT LOG

Record sensitive administrative operations:

```text
ADMIN_USER
ACTION
ENTITY_TYPE
ENTITY_ID
OLD_VALUE
NEW_VALUE
TIMESTAMP
IP / REQUEST METADATA where legally appropriate
REASON
```

Examples:

```text
Approve vendor
Reject vendor
Suspend vendor
Refund order
Cancel order
Change commission
Change service zone
Deactivate rider
Modify menu
```

---

# 29. OBSERVABILITY

Every backend request should have a request/correlation ID.

Log:

- request ID
- authenticated user ID where appropriate
- endpoint
- status
- latency
- error code
- relevant entity ID

Do not log:

- OTP
- passwords
- payment secrets
- full authentication tokens
- sensitive personal data unnecessarily

Add health endpoints for:

```text
API
PostgreSQL/Supabase
Redis
```

---

# 30. ERROR HANDLING

The client applications must distinguish:

```text
NETWORK_ERROR
AUTH_ERROR
VALIDATION_ERROR
BUSINESS_RULE_ERROR
PAYMENT_ERROR
NOT_FOUND
PERMISSION_DENIED
SERVER_ERROR
```

Use friendly UI messages while retaining machine-readable error codes.

Example:

```text
ORDER_SLOT_FULL
VENDOR_NOT_AVAILABLE
PAYMENT_FAILED
RIDER_NOT_AVAILABLE
ADDRESS_NOT_SERVICEABLE
SUBSCRIPTION_PAYMENT_FAILED
```

---

# 31. MOBILE APPLICATION SHARED BEHAVIOR

The three React Native applications should have consistent:

- API client
- authentication handling
- token refresh
- error handling
- loading states
- network retry strategy
- deep-link strategy
- push notification handling
- environment configuration
- analytics hooks
- logging conventions

Do not copy-paste large amounts of identical code between the three apps if a shared package can safely contain it.

However, avoid forcing all UI into one universal mobile application. Customer, Vendor and Rider are different products and should retain separate navigation and feature modules.

---

# 32. CUSTOMER APP CORE SCREENS

At minimum:

```text
Splash
Onboarding
Mobile Login
OTP
Profile
Address Management
Home
Search
Hometown/Cuisine Discovery
Vendor List
Vendor Detail
Menu
Item Detail
Cart
Checkout
Payment
Order Tracking
Order Detail
Order History
Subscription Plans
Subscription Detail
Meal Calendar
Notifications
Reviews
Profile
Help/Support
```

---

# 33. VENDOR APP CORE SCREENS

```text
Splash
Login/OTP
Vendor Onboarding
Verification Status
Dashboard
Today's Orders
Order Detail
Menu
Menu Item Editor
Availability
Meal Slots
Capacity
Subscription Calendar
Delivery/Pickup Status
Earnings
Payouts
Notifications
Profile
Documents
Support
```

---

# 34. RIDER APP CORE SCREENS

```text
Splash
Login/OTP
KYC/Onboarding
Availability Toggle
Dashboard
Delivery Requests
Assignment Detail
Navigation
Vendor Pickup
QR Scanner
Pickup Confirmation
Active Delivery
Customer Delivery
OTP/Proof of Delivery
Delivery History
Earnings
Payouts
Notifications
Profile
Support
```

---

# 35. ADMIN WEB CORE SCREENS

```text
Login
Dashboard
Customers
Vendors
Vendor Verification
Vendor Detail
Vendor Menu
Riders
Rider Verification
Orders
Order Detail
Subscriptions
Deliveries
Service Zones
Cities/Areas
Meal Slots
Payments
Refunds
Commissions
Vendor Payouts
Rider Payouts
Coupons
Reviews
Support
Notifications
Audit Logs
Admin Users/Roles
Settings
```

---

# 36. FRONTEND API LAYER

Each client should use an API abstraction rather than making random fetch/axios calls throughout screens.

Example:

```text
api/
├── auth.ts
├── users.ts
├── vendors.ts
├── menus.ts
├── cart.ts
├── orders.ts
├── subscriptions.ts
├── payments.ts
├── deliveries.ts
├── notifications.ts
└── support.ts
```

The exact implementation may follow the existing project's architecture.

---

# 37. ENVIRONMENT MANAGEMENT

Provide:

```text
.env.example
.env.development
.env.staging
.env.production
```

Never commit secrets.

Example public client variables:

```text
API_BASE_URL
SUPABASE_URL
SUPABASE_ANON_KEY
```

Server-only variables:

```text
SUPABASE_SERVICE_ROLE_KEY
DATABASE_URL
REDIS_URL
PAYMENT_SECRET
PAYMENT_WEBHOOK_SECRET
OTP_PROVIDER_SECRET
```

Use environment-specific configuration.

---

# 38. LOCAL DEVELOPMENT

The repository should support a practical local development workflow.

Example:

```bash
docker compose up -d redis
```

Then:

```bash
cd services/api
./mvnw spring-boot:run
```

Customer:

```bash
cd apps/customer-mobile
npm install
npm run start
```

Vendor:

```bash
cd apps/vendor-mobile
npm install
npm run start
```

Rider:

```bash
cd apps/rider-mobile
npm install
npm run start
```

Admin:

```bash
cd apps/admin-web
npm install
npm run dev
```

Adapt commands to the actual package manager and existing React Native setup.

---

# 39. TESTING STRATEGY

## Backend

Implement:

- unit tests
- controller/API tests
- service tests
- repository tests where useful
- state transition tests
- payment webhook tests
- subscription idempotency tests
- authorization tests

Critical test cases:

```text
Customer cannot access another customer's order.
Vendor cannot modify another vendor's menu.
Rider cannot update another rider's delivery.
Admin-only endpoints reject normal users.
Duplicate payment webhook does not duplicate payment.
Duplicate subscription job does not create duplicate order.
Full meal slot rejects new order.
Cancelled order cannot become completed.
Completed order cannot be picked up again.
```

## Mobile/Web

Test:

- authentication
- navigation
- API error states
- empty states
- loading states
- offline/network failure
- order lifecycle
- subscription lifecycle
- role-based screens

---

# 40. SECURITY CHECKLIST

Implement:

- JWT validation
- role-based authorization
- ownership authorization
- rate limiting
- OTP throttling
- secure password/auth handling through Supabase
- webhook signature validation
- input validation
- output sanitization where needed
- secure file upload validation
- private storage for sensitive documents
- CORS configuration
- secure headers
- no secrets in source control
- audit logging
- idempotency for financial/order operations

Do not rely on frontend authorization.

---

# 41. PRIVACY

Only collect data required for the product.

Protect:

- phone numbers
- addresses
- location data
- identity/KYC documents
- payment references
- customer/vendor/rider personal information

Customer location should only be shared with operational actors when required for delivery.

A Rider should not receive unnecessary customer profile information.

A Vendor should not receive unnecessary payment information.

---

# 42. API DOCUMENTATION

Generate and maintain OpenAPI documentation.

Every API should specify:

- endpoint
- HTTP method
- authentication
- roles
- request schema
- response schema
- validation
- errors
- examples

Keep API contracts synchronized with clients.

---

# 43. MASTER BUSINESS FLOW

The complete system should operate approximately as follows:

```text
CUSTOMER
   │
   ├── Login via OTP
   │
   ├── Set profile + address
   │
   ├── Discover hometown/cuisine food
   │
   ├── Select vendor
   │
   ├── Select meal
   │
   ├── Normal order OR subscription
   │
   └── Payment
          │
          ▼
SPRING BOOT API
          │
          ├── Validate user
          ├── Validate vendor
          ├── Validate serviceability
          ├── Validate slot
          ├── Validate capacity
          ├── Calculate price
          ├── Create order/subscription
          ├── Process payment
          └── Publish operational events
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
      VENDOR APP            RIDER APP
          │                    │
          ├── Accept           ├── Assignment
          ├── Prepare          ├── Accept
          ├── Ready            ├── Pickup QR
          └── Handoff          ├── Pickup
                               ├── Deliver
                               └── Confirm
                                      │
                                      ▼
                                  CUSTOMER
                                      │
                                      ├── Confirm
                                      └── Review

                    ADMIN WEB
                        │
                        ├── Vendor verification
                        ├── Rider management
                        ├── Customer management
                        ├── Orders
                        ├── Subscriptions
                        ├── Deliveries
                        ├── Payments/refunds
                        ├── Payouts
                        ├── Service zones
                        └── Platform operations
```

---

# 44. ADMIN ↔ MOBILE APP LINKAGE

The four applications are not independent systems.

The linkage must be through the shared backend/domain.

Example:

```text
Admin approves vendor
       ↓
Vendor status = APPROVED
       ↓
Vendor app becomes operational
       ↓
Vendor publishes menu
       ↓
Customer app discovers vendor
       ↓
Customer places order
       ↓
Backend creates order
       ↓
Vendor receives order
       ↓
Vendor prepares
       ↓
Backend requests/creates rider assignment
       ↓
Rider receives delivery
       ↓
Rider picks up
       ↓
Customer tracks delivery
       ↓
Delivery completed
       ↓
Order completed
       ↓
Payout/commission records generated
       ↓
Admin sees final transaction
```

This linkage must be implemented in actual APIs/database/events, not merely mocked in the UI.

---

# 45. PAYMENT → ORDER CONSISTENCY

Financial operations require special care.

Use an idempotency key for:

```text
create payment
confirm payment
create order
refund
subscription payment
```

Never create two orders because a user double-tapped the payment button.

Never mark an order paid solely because the app returned from a payment screen.

Use:

```text
Payment provider webhook
+
server-side verification
+
idempotent processing
```

---

# 46. CANCELLATION AND REFUND

Define cancellation policies by stage.

Example configurable rules:

```text
Before vendor acceptance
During preparation
After food ready
After pickup
After delivery
```

The backend determines eligibility.

Refund amount must be calculated server-side.

Every refund must create an auditable record.

---

# 47. RATING AND REVIEW

After eligible completion:

Customer may rate:

```text
Vendor/home cook
Food
Rider/delivery
```

Prevent duplicate reviews for the same order unless explicitly supported.

Moderation/admin controls should exist.

Do not expose abusive/private review metadata unnecessarily.

---

# 48. SUPPORT

Create support ticket model:

```text
Ticket
TicketMessage
TicketAttachment
TicketStatus
TicketPriority
AssignedAdmin
```

Customer, Vendor and Rider can create tickets relevant to their role.

Admin can assign and resolve tickets.

---

# 49. FINANCE

Separate:

```text
Order total
Food subtotal
Delivery fee
Platform fee
Tax
Discount
Vendor earning
Rider earning
Platform commission
Payment gateway fee
Refund
```

Do not derive historical financial reports only from current menu prices.

Store transaction snapshots where necessary.

---

# 50. ANALYTICS

Prepare backend events for:

```text
USER_REGISTERED
OTP_VERIFIED
VENDOR_REGISTERED
VENDOR_APPROVED
MENU_VIEWED
ITEM_ADDED_TO_CART
CHECKOUT_STARTED
PAYMENT_STARTED
PAYMENT_SUCCESS
ORDER_CREATED
ORDER_ACCEPTED
ORDER_PREPARING
ORDER_READY
RIDER_ASSIGNED
ORDER_PICKED_UP
ORDER_DELIVERED
ORDER_COMPLETED
SUBSCRIPTION_CREATED
SUBSCRIPTION_ACTIVATED
SUBSCRIPTION_PAUSED
SUBSCRIPTION_CANCELLED
```

Analytics must not replace operational database records.

---

# 51. UI/UX REQUIREMENTS

The three mobile apps are role-specific.

### Customer UI
Focus on:

- discovering hometown food
- trust
- vendor authenticity
- menu clarity
- simple checkout
- delivery tracking

### Vendor UI
Focus on:

- today's workload
- meal capacity
- order preparation
- subscriptions
- earnings

### Rider UI
Focus on:

- assignment clarity
- navigation
- pickup verification
- delivery verification
- minimal interaction while travelling

### Admin UI
Focus on:

- operational visibility
- filtering
- tables
- auditability
- bulk actions where safe

Preserve the visual language of the existing apps unless there is a clear reason to standardize it.

---

# 52. ACCESSIBILITY

Support:

- readable typography
- sufficient contrast
- screen reader labels
- touch targets
- keyboard navigation for web
- meaningful error messages
- semantic form controls

---

# 53. PERFORMANCE

Customer:

- lazy-load heavy screens
- optimize food images
- cache safe data
- avoid excessive polling
- use pagination

Vendor:

- prioritize today's operational data

Rider:

- minimize battery/GPS usage
- update location only at an appropriate operational frequency
- handle poor network conditions

Admin:

- server-side pagination
- filtering
- debounced search
- virtualized large tables where appropriate

Backend:

- database indexes
- Redis caching where useful
- avoid N+1 queries
- pagination
- async processing for non-critical operations

---

# 54. OFFLINE / NETWORK RESILIENCE

Mobile applications should gracefully handle:

```text
No internet
Slow internet
Request timeout
API unavailable
Token expiry
Temporary server failure
```

Do not allow offline actions to falsely appear as completed server actions.

For Rider operations, pickup/delivery confirmation should have careful retry/idempotency behavior.

---

# 55. MIGRATION STRATEGY FOR EXISTING FRONTENDS

For each existing mobile app:

### Step 1
Map existing screens to product requirements.

### Step 2
Identify existing mock APIs.

### Step 3
Create the production API layer.

### Step 4
Replace mock data gradually.

### Step 5
Connect authentication.

### Step 6
Connect real domain entities.

### Step 7
Connect real-time/order state.

### Step 8
Add error/loading/empty states.

### Step 9
Remove obsolete mock flows.

### Step 10
Run end-to-end testing.

Do not rewrite everything just because the backend architecture is changing.

---

# 56. RECOMMENDED BACKEND PACKAGE STRUCTURE

Use a modular Spring Boot structure such as:

```text
com.codewild.food
├── config
├── security
├── common
│   ├── exception
│   ├── response
│   ├── audit
│   └── idempotency
├── auth
├── user
├── customer
├── vendor
├── rider
├── menu
├── cart
├── order
├── payment
├── subscription
├── delivery
├── serviceability
├── slot
├── notification
├── review
├── support
├── finance
└── admin
```

Each domain should preferably contain:

```text
controller
service
repository
entity
dto
mapper
validator
```

Avoid creating a huge controller/service containing the entire platform.

---

# 57. RECOMMENDED REPOSITORY BOUNDARIES

```text
apps/
  customer-mobile
  vendor-mobile
  rider-mobile
  admin-web

services/
  api

packages/
  api-contracts
  shared-types
  validation

supabase/
  migrations
  seed

infra/
  docker

docs/
  architecture
  workflows
  database
  api
```

The exact package manager/monorepo technology may be selected after inspecting the existing projects.

If a full JS monorepo tool would create unnecessary migration risk for React Native, use a simple workspace arrangement and keep native project boundaries intact.

---

# 58. DEFINITION OF DONE

The integration is not complete when screens compile.

It is complete only when:

### Customer
- can authenticate
- manage profile/address
- discover food
- view vendor/menu
- create cart
- pay
- track order
- complete delivery
- review

### Vendor
- can authenticate
- complete onboarding
- be approved by Admin
- manage menu
- manage availability
- receive orders
- prepare orders
- mark ready
- manage subscriptions
- see earnings

### Rider
- can authenticate
- complete onboarding
- be approved
- go online/offline
- receive assignment
- accept delivery
- verify pickup
- deliver
- verify delivery
- see history/earnings

### Admin
- can authenticate
- manage roles
- approve vendors
- manage riders
- manage customers
- manage orders
- manage subscriptions
- manage service zones
- manage payments/refunds
- manage commissions/payouts
- view audit logs

### Backend
- all critical workflows are server authoritative
- permissions are enforced
- payment webhooks are secure/idempotent
- subscription generation is idempotent
- order state transitions are validated
- Redis is used appropriately
- Supabase is integrated correctly
- API documentation exists

---

# 59. DELIVERY PHASES

Implement in this order.

## Phase 0 — Repository discovery

Do not write feature code yet.

Produce:

```text
existing architecture report
existing dependency report
existing screen map
existing API/mock map
existing environment map
integration risks
```

## Phase 1 — Repository consolidation

Create/link:

```text
customer-mobile
vendor-mobile
rider-mobile
admin-web
services/api
packages
supabase
infra
docs
```

Ensure all projects build independently.

## Phase 2 — Authentication

Implement:

```text
Supabase Auth
Spring Security JWT validation
Customer role
Vendor role
Rider role
Admin role
```

## Phase 3 — Core profiles

Implement:

```text
Customer
Vendor
Rider
Admin
Address
Documents
```

## Phase 4 — Vendor/menu

Implement:

```text
Vendor onboarding
Admin approval
Menu
Categories
Items
Availability
Slots
Capacity
```

## Phase 5 — Customer discovery/cart

Implement:

```text
Discovery
Hometown/cuisine filters
Vendor detail
Menu
Cart
Checkout
```

## Phase 6 — Payments/orders

Implement:

```text
Payment
Order
Order state machine
Payment webhooks
```

## Phase 7 — Vendor operations

Implement:

```text
Accept
Prepare
Ready
Pickup handoff
```

## Phase 8 — Rider operations

Implement:

```text
Assignment
Accept
QR pickup
Delivery
OTP/proof
```

## Phase 9 — Tracking/notifications

Implement:

```text
Realtime status
Push notifications
Order timeline
```

## Phase 10 — Subscriptions

Implement:

```text
Meal plans
Subscription
Calendar
Daily order generation
Subscription payment
Pause/skip/cancel
```

## Phase 11 — Admin operations

Implement full admin modules.

## Phase 12 — Finance/support

Implement:

```text
Commission
Payouts
Refunds
Support
Audit
```

## Phase 13 — Hardening

Implement:

```text
security
rate limits
idempotency
logging
monitoring
tests
performance
error handling
```

---

# 60. AI CODING AGENT OPERATING INSTRUCTIONS

When executing this master prompt, follow these rules.

## Rule 1 — Do not invent existing code

Inspect the repository.

If an existing implementation already solves a requirement, integrate it.

## Rule 2 — Do not delete working code without reason

Prefer incremental migration.

## Rule 3 — Never hardcode business state in the frontend

Example:

Bad:

```ts
if (order.status === "READY") {
   // assume rider is assigned
}
```

Good:

```text
Use server-provided order state and allowed transitions/actions.
```

## Rule 4 — Never trust client prices

The backend calculates final price.

## Rule 5 — Never trust client payment success

Use server verification/webhooks.

## Rule 6 — Never trust client role

Role comes from authenticated server-side identity.

## Rule 7 — Make mutations idempotent

Especially:

```text
payments
orders
subscriptions
pickup
delivery confirmation
refunds
webhooks
```

## Rule 8 — Keep secrets server-side

## Rule 9 — Every important operation must be traceable

Use:

```text
requestId
entityId
userId
audit/event record
```

where appropriate.

## Rule 10 — Update documentation while implementing

Do not postpone architecture/API documentation until the end.

---

# 61. REQUIRED OUTPUT FROM THE CODING AGENT

Before implementation:

```text
1. Repository assessment
2. Proposed final folder structure
3. Existing-code migration plan
4. Database/entity plan
5. API plan
6. Authentication plan
7. Order state machine
8. Subscription state machine
9. Delivery state machine
10. Admin permission matrix
```

During implementation:

```text
1. Keep code compiling
2. Keep migrations versioned
3. Keep API contracts documented
4. Add tests with important business logic
5. Do not leave critical mock data
```

At the end:

```text
1. Final architecture
2. Setup instructions
3. Environment variable list
4. Database migration instructions
5. Supabase setup
6. Redis setup
7. Backend startup
8. Customer startup
9. Vendor startup
10. Rider startup
11. Admin startup
12. Test credentials for local development
13. API documentation location
14. Known limitations
15. Production deployment checklist
```

---

# 62. ACCEPTANCE TEST — COMPLETE BUSINESS SCENARIO

Use this as the primary end-to-end acceptance test.

### Scenario

A customer from Coimbatore is living in Chennai.

A verified home cook in Chennai provides Coimbatore-style home food.

### Setup

Admin:

```text
1. Approves vendor.
2. Vendor publishes Coimbatore-style meals.
3. Vendor configures lunch slot.
4. Vendor sets capacity.
5. Service zone includes customer's address.
```

### Customer

```text
1. Opens Customer app.
2. Enters mobile number.
3. Verifies OTP.
4. Sets Chennai delivery address.
5. Searches/discovers Coimbatore-style food.
6. Opens home cook.
7. Selects meal.
8. Adds to cart.
9. Checks out.
10. Pays using supported payment method.
```

### Backend

```text
11. Validates authentication.
12. Validates vendor.
13. Validates serviceability.
14. Validates slot.
15. Validates capacity.
16. Calculates total.
17. Creates payment.
18. Verifies payment.
19. Creates order.
20. Notifies vendor.
```

### Vendor

```text
21. Receives order.
22. Accepts order.
23. Starts preparation.
24. Marks ready.
25. Rider assignment is created.
```

### Rider

```text
26. Receives delivery assignment.
27. Accepts assignment.
28. Arrives at vendor.
29. Scans pickup QR.
30. Backend validates QR.
31. Order becomes PICKED_UP.
32. Rider travels to customer.
33. Rider completes delivery verification.
34. Backend marks order DELIVERED.
```

### Customer

```text
35. Receives delivery status.
36. Confirms delivery where required.
37. Order becomes COMPLETED.
38. Customer reviews home cook and rider.
```

### Admin

```text
39. Admin can inspect complete order timeline.
40. Payment is visible.
41. Vendor earning/commission is calculated.
42. Rider earning is calculated.
43. Audit events exist.
```

If any of these steps only work through mocked frontend state, the integration is incomplete.

---

# 63. SECOND ACCEPTANCE TEST — SUBSCRIPTION

```text
Customer logs in
    ↓
Chooses subscription
    ↓
Selects home cook
    ↓
Selects meal plan
    ↓
Selects days/slot
    ↓
Pays
    ↓
Subscription becomes ACTIVE
    ↓
Daily order generator creates today's order
    ↓
Vendor receives today's meal
    ↓
Vendor prepares
    ↓
Rider receives delivery
    ↓
Rider picks up
    ↓
Customer receives meal
    ↓
Daily order completed
    ↓
Next scheduled meal remains pending
```

Verify:

```text
No duplicate daily order
No unauthorized subscription modification
Failed payment is handled
Paused subscription stops appropriate future meals
Cancelled subscription stops future generation
```

---

# 64. FINAL PRINCIPLE

The platform should be engineered as one connected ecosystem, not four separate applications.

The relationship is:

```text
                    CODEWILD FOOD PLATFORM
                             │
            ┌────────────────┼────────────────┐
            │                │                │
        CUSTOMER          VENDOR            RIDER
          APP            HOME COOK           APP
            │                │                │
            └────────────────┼────────────────┘
                             │
                       SPRING BOOT
                        BUSINESS API
                             │
                 ┌───────────┴───────────┐
                 │                       │
             SUPABASE                  REDIS
                 │
          PostgreSQL/Auth/
             Storage
                 │
                         ADMIN WEB
                       React + Vite
```

The product's differentiating value is not merely food delivery.

It is the **connection between people living away from their hometown and verified local home cooks who can provide familiar regional food**.

The architecture must therefore make these concepts first-class:

```text
Customer location
+
Customer hometown / preferred region
+
Cuisine / regional food identity
+
Verified home cook
+
Meal availability
+
Serviceability
+
Delivery
+
Subscription
```

Build the system so that Chennai/Coimbatore is only an example. The same architecture must support:

```text
Madurai food in Chennai
Tirunelveli food in Bengaluru
Kerala food in Chennai
Andhra food in Hyderabad
Bengali food in Delhi
North Indian home food in Bengaluru
etc.
```

Do not hardcode individual cities, cuisines, or communities into business logic.

The final product should be extensible to additional cities, regions, cuisines, vendors, riders, payment providers and delivery models without rewriting the platform.

---

# 65. SHORT COMMAND TO THE AI CODING AGENT

Use the following as the execution command after placing this file in the repository:

> **"Read `MASTER_PROMPT.md` completely. Inspect the existing Customer, Vendor/Home-Cook and Rider React Native applications before changing anything. Inspect the Admin React/Vite application if present. Consolidate the projects into one maintainable repository, create/connect the Spring Boot backend, integrate Supabase Auth/PostgreSQL/Storage, integrate Redis, and connect all clients through the backend. Implement the business workflows, roles, state machines, payments, subscriptions, vendor onboarding, rider delivery, notifications, serviceability, admin operations, audit and finance described in this document. Preserve useful existing frontend code and UI. Do not replace working applications blindly. Do not use mock data for critical production workflows. Implement incrementally, keep the project buildable, write migrations/tests/API documentation, and finish with setup and deployment documentation."**


---

# 48. ARCHITECTURE-FIRST IMPLEMENTATION CHECKLIST

Before writing feature code:

- [ ] Inspect existing repository
- [ ] Confirm React Native/Expo setup
- [ ] Confirm package manager
- [ ] Map existing navigation
- [ ] Map existing API/mock services
- [ ] Map existing authentication
- [ ] Map existing state management
- [ ] Confirm Admin React/Vite structure
- [ ] Create architecture ADRs
- [ ] Define domain ownership
- [ ] Define API boundary
- [ ] Define database ownership
- [ ] Define state machines
- [ ] Define event strategy
- [ ] Define idempotency strategy
- [ ] Define deployment environments

Before calling integration complete:

- [ ] Customer → Vendor → Rider → Customer workflow works through real backend
- [ ] Admin controls the same backend data
- [ ] No critical mock workflow remains
- [ ] Payment verification is server-side
- [ ] Order transitions are server-side
- [ ] Vendor approval is server-side
- [ ] Rider authorization is server-side
- [ ] Subscription generation is idempotent
- [ ] Financial records are auditable
- [ ] Sensitive files are private
- [ ] Logs do not expose secrets
- [ ] Critical events cannot silently disappear
- [ ] Database migrations are reproducible
- [ ] API documentation is current
- [ ] Tests cover critical business rules
- [ ] Production configuration is separated from development

---

# 49. DOCUMENT STATUS

```text
Document: MASTER_PROMPT.md
Edition: Architecture Edition v2.0
Organization: Codewild Tech Pvt Ltd
Product: Home-Made Food Delivery / Regional Home Food Marketplace
Architecture: Modular Monolith + Domain Boundaries + Event-Driven Integration
Clients:
  - Customer React Native
  - Vendor/Home-Cook React Native
  - Rider React Native
  - Admin React + Vite

Backend:
  - Spring Boot / Java

Platform:
  - Supabase PostgreSQL
  - Supabase Auth
  - Supabase Storage
  - Redis

Primary objective:
  One connected platform with four clients and one authoritative business backend.
```
