# WMS V4.5 Architecture Design

**Document status:** Proposed
**Baseline:** V4.4
**Date:** 2026-06-14
**Scope:** Inventory reservation, dual state machines, backorder readiness, emergency stock correction
**Important:** This document is an architecture proposal. It does not apply database migrations or change V4.4 runtime behavior.

---

## 1. Purpose

V4.4 has completed the core inbound and outbound flow:

- Batch inventory.
- FEFO allocation.
- Full-pack and loose-unit allocation.
- Outbound task confirmation.
- Transactional batch confirmation.
- Oversell rejection during approval.
- Stocktake-based inventory reconciliation.

V4.5 will strengthen the boundary between commercial demand and physical fulfillment.

The target architecture must support:

1. Recording customer demand even when stock is insufficient.
2. Reserving stock atomically when the business commits to fulfillment.
3. Separating order approval from warehouse execution.
4. Supporting partial allocation and future backorders.
5. Using controlled stock correction instead of normal negative inventory.
6. Preserving batch, location, expiry, cost and audit traceability.

---

## 2. Architecture Principles

### 2.1 Commercial intent is not physical execution

Creating a sales order records customer demand. It does not guarantee stock.

```text
Create order
    = record commercial intent

Approve order
    = accept commercial commitment

Allocate and reserve
    = make inventory commitment

Confirm picking
    = perform physical stock movement
```

### 2.2 Inventory must never be promised twice

Available stock must be calculated as:

```text
Available = OnHand - Reserved
```

Where:

- `OnHand` is the physical quantity recorded in an inventory batch.
- `Reserved` is the quantity committed to approved orders but not yet shipped.
- `Available` is the quantity that can be promised to another order.

### 2.3 Commercial and fulfillment states must be independent

A commercially approved order can still be:

- Fully allocated.
- Partially allocated.
- Waiting for inbound stock.
- Backordered.
- Picking.
- Shipped.

One status field cannot express both business decisions and warehouse progress safely.

### 2.4 Physical batch stock must not become negative

For food and batch-tracked goods, negative inventory destroys:

- Batch traceability.
- Expiry traceability.
- Location accuracy.
- Cost attribution.
- Recall capability.
- Financial audit integrity.

When physical stock exists but system stock is wrong, the system should correct the stock record through an auditable emergency process before shipment.

### 2.5 Every quantity change must be auditable

Reservation, release, shipment and stock correction must record:

- Source document.
- Source line.
- Operator.
- Timestamp.
- Quantity before and after.
- Reason.
- Correlation or idempotency key.

---

## 3. Target Domain Model

### 3.1 Quantity model

```text
OnHand
  Physical quantity recorded in InventoryBatch.quantity.

Reserved
  Active inventory reservations not yet released or consumed.

Available
  OnHand - Reserved.

Allocated
  Quantity assigned to concrete batches and locations.

Backordered
  Approved demand that could not be allocated.

InTransit
  Confirmed purchase quantity not yet physically received.

ATP
  Available + trusted inbound supply before promise date
  - previously committed future demand.
```

### 3.2 Required invariants

The following rules must always hold:

```text
OnHand >= 0
Reserved >= 0
Available >= 0
Reserved <= OnHand

OrderItem.requestedQty
  = allocatedQty + backorderQty + cancelledQty

Reservation.reservedQty
  = consumedQty + releasedQty + activeQty
```

No transaction may leave a batch with `Reserved > OnHand`.

---

## 4. Inventory Reservation Model

## 4.1 Recommended model

Use a dedicated reservation ledger as the source of truth. A reservation row links:

- Sales order.
- Sales order item.
- Inventory batch.
- Location.
- Reserved quantity.
- Reservation lifecycle.

Do not rely only on outbound tasks as reservations. Outbound tasks describe warehouse work; reservations describe inventory commitments.

## 4.2 Proposed table: `inventory_reservation`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | BIGINT | No | Primary key |
| `reservation_no` | VARCHAR(50) | No | External reservation identifier |
| `sales_order_id` | BIGINT | No | Sales order |
| `sales_order_item_id` | BIGINT | No | Sales order line |
| `inventory_batch_id` | BIGINT | No | Reserved batch |
| `location_id` | BIGINT | No | Reserved location |
| `product_id` | BIGINT | No | Redundant query key |
| `reserved_qty` | INTEGER | No | Originally reserved quantity |
| `consumed_qty` | INTEGER | No | Quantity converted into shipment |
| `released_qty` | INTEGER | No | Quantity released by cancellation or reallocation |
| `status` | VARCHAR(30) | No | Reservation lifecycle |
| `expires_at` | TIMESTAMP | Yes | Optional reservation timeout |
| `source_type` | VARCHAR(30) | No | SALES_ORDER, MANUAL, REPLAN |
| `idempotency_key` | VARCHAR(100) | Yes | Prevent duplicate reservation |
| `created_by` | BIGINT | Yes | Creator |
| `created_at` | TIMESTAMP | No | Creation time |
| `updated_at` | TIMESTAMP | No | Last update |
| `version` | BIGINT | No | Optimistic lock version |

Recommended reservation statuses:

```text
ACTIVE
PARTIALLY_CONSUMED
CONSUMED
RELEASED
EXPIRED
```

Active reservation quantity:

```text
activeQty = reservedQty - consumedQty - releasedQty
```

Recommended constraints:

```sql
CHECK (reserved_qty > 0)
CHECK (consumed_qty >= 0)
CHECK (released_qty >= 0)
CHECK (consumed_qty + released_qty <= reserved_qty)
UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
```

Recommended indexes:

```text
(inventory_batch_id, status)
(sales_order_id, status)
(sales_order_item_id, status)
(product_id, status)
(expires_at, status)
```

## 4.3 Proposed changes to `inventory_batch`

Preferred source-of-truth model:

- Keep `quantity` as OnHand.
- Calculate Reserved from active reservation rows.
- Calculate Available in SQL.

Optional performance field:

| Column | Type | Default | Description |
|---|---|---:|---|
| `reserved_quantity` | INTEGER | 0 | Cached active reservation total |

If `reserved_quantity` is added, it is a transactional cache and must be updated in the same transaction as `inventory_reservation`.

Required database checks:

```sql
CHECK (quantity >= 0)
CHECK (reserved_quantity >= 0)
CHECK (reserved_quantity <= quantity)
```

The preferred query becomes:

```sql
available_quantity = quantity - reserved_quantity
```

## 4.4 Reservation transaction

Approval and reservation must run in one transaction:

```text
Load order and validate commercial state
    ↓
Lock or version-check candidate batches
    ↓
Calculate Available = OnHand - Reserved
    ↓
Run FEFO and pack-aware allocation
    ↓
Insert reservation rows
    ↓
Increment cached reserved_quantity, if used
    ↓
Create outbound tasks from reservations
    ↓
Update fulfillment state
    ↓
Commit
```

If any line cannot satisfy the selected fulfillment policy, the transaction must roll back.

## 4.5 Reservation consumption

When an outbound task is confirmed:

```text
Validate task and reservation
    ↓
Deduct InventoryBatch.quantity
    ↓
Decrease active reserved quantity
    ↓
Increase reservation.consumedQty
    ↓
Complete outbound task
    ↓
Write stock transaction
    ↓
Recalculate order fulfillment state
```

Cancellation releases reservation without changing OnHand.

---

## 5. Dual State Machine

## 5.1 Commercial state

Commercial state answers:

> Has the company accepted this order as a business commitment?

Recommended values:

```text
DRAFT
PENDING_APPROVAL
APPROVED
REJECTED
CANCELLED
VOIDED
```

Recommended transitions:

```text
DRAFT
  → PENDING_APPROVAL
  → CANCELLED / VOIDED

PENDING_APPROVAL
  → APPROVED
  → REJECTED
  → CANCELLED / VOIDED

APPROVED
  → CANCELLED

REJECTED / CANCELLED / VOIDED
  → terminal
```

## 5.2 Fulfillment state

Fulfillment state answers:

> How much of the approved order can the warehouse currently fulfill?

Recommended values:

```text
UNALLOCATED
ALLOCATING
PARTIALLY_ALLOCATED
ALLOCATED
WAITING_INBOUND
BACKORDERED
PICKING
PARTIALLY_SHIPPED
SHIPPED
FULFILLMENT_CANCELLED
```

Recommended transitions:

```text
UNALLOCATED
  → ALLOCATING

ALLOCATING
  → ALLOCATED
  → PARTIALLY_ALLOCATED
  → BACKORDERED
  → WAITING_INBOUND

PARTIALLY_ALLOCATED
  → ALLOCATED
  → PICKING
  → BACKORDERED

ALLOCATED
  → PICKING

PICKING
  → PARTIALLY_SHIPPED
  → SHIPPED

BACKORDERED / WAITING_INBOUND
  → ALLOCATING
```

## 5.3 Proposed changes to `sales_orders`

Current `status` should be migrated carefully rather than reinterpreted silently.

Proposed fields:

| Column | Type | Default | Description |
|---|---|---|---|
| `commercial_status` | VARCHAR(40) | DRAFT | Approval and commercial lifecycle |
| `fulfillment_status` | VARCHAR(40) | UNALLOCATED | Warehouse fulfillment lifecycle |
| `approved_at` | TIMESTAMP | NULL | Commercial approval time |
| `requested_ship_date` | DATE | NULL | Customer requested ship date |
| `promised_ship_date` | DATE | NULL | System or manager commitment |
| `allocation_policy` | VARCHAR(30) | FULL_ONLY | FULL_ONLY or ALLOW_PARTIAL |
| `shortage_reason` | VARCHAR(500) | NULL | Current fulfillment shortage |
| `fulfillment_version` | BIGINT | 0 | Optimistic lock for fulfillment updates |

Compatibility approach:

1. Add new columns.
2. Backfill from current `status`.
3. Continue returning legacy `status` during a transition window.
4. Add explicit `commercialStatus` and `fulfillmentStatus` to API responses.
5. Remove legacy interpretation only in a later major version.

## 5.4 Proposed changes to `sales_order_items`

| Column | Type | Default | Description |
|---|---|---:|---|
| `requested_qty` | INTEGER | Existing quantity | Original customer demand |
| `allocated_qty` | INTEGER | 0 | Quantity currently reserved |
| `shipped_qty` | INTEGER | 0 | Quantity physically shipped |
| `backorder_qty` | INTEGER | 0 | Approved but unallocated quantity |
| `cancelled_qty` | INTEGER | 0 | Cancelled remainder |
| `requested_ship_date` | DATE | NULL | Optional line-level date |
| `promised_ship_date` | DATE | NULL | Promise for this line |
| `fulfillment_status` | VARCHAR(40) | UNALLOCATED | Line fulfillment state |

Required check:

```text
requestedQty
  = allocatedQty
  + shippedQty not still allocated
  + backorderQty
  + cancelledQty
```

The exact check must account for whether `allocatedQty` includes shipped quantities. The recommended interpretation is:

```text
requestedQty = activeAllocatedQty + shippedQty + backorderQty + cancelledQty
```

---

## 6. Backorder Readiness

Backorder is recommended as the primary shortage strategy.

## 6.1 Allocation policies

```text
FULL_ONLY
  Approval succeeds only when the entire order can be reserved.

ALLOW_PARTIAL
  Reserve available stock and place the remainder into backorder.

WAIT_FOR_COMPLETE
  Approve commercially but create no warehouse task until all stock is ready.
```

Default policy for V4.5 should remain `FULL_ONLY` to preserve V4.4 behavior.

## 6.2 Proposed table: `backorder_line`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | BIGINT | No | Primary key |
| `sales_order_id` | BIGINT | No | Sales order |
| `sales_order_item_id` | BIGINT | No | Sales order line |
| `product_id` | BIGINT | No | Product |
| `requested_qty` | INTEGER | No | Original shortage quantity |
| `remaining_qty` | INTEGER | No | Quantity still waiting |
| `allocated_qty` | INTEGER | No | Quantity later allocated |
| `status` | VARCHAR(30) | No | OPEN, PARTIAL, FULFILLED, CANCELLED |
| `priority` | INTEGER | No | Allocation priority |
| `promised_date` | DATE | Yes | Customer promise date |
| `created_at` | TIMESTAMP | No | Created time |
| `updated_at` | TIMESTAMP | No | Updated time |
| `version` | BIGINT | No | Optimistic lock |

## 6.3 Backorder wake-up

After inbound receipt commits:

1. Publish an `INVENTORY_AVAILABLE` domain event.
2. Find open backorders for affected products.
3. Process by priority and creation time.
4. Attempt reservation using the same allocation service.
5. Update backorder and fulfillment states.
6. Create outbound tasks only for successfully reserved quantities.

Use an outbox table to avoid losing the wake-up event after database commit.

Proposed `domain_outbox` fields:

```text
id
event_type
aggregate_type
aggregate_id
payload_json
status
retry_count
next_retry_at
created_at
published_at
```

---

## 7. In-Transit Inventory and ATP

In-transit inventory is a promise signal, not physical inventory.

## 7.1 Source data

Use confirmed purchase order lines where:

```text
PurchaseOrder.status IN (IN_TRANSIT, PARTIALLY_RECEIVED)
remainingInboundQty = orderedQty - receivedQty
```

## 7.2 Proposed purchase fields

| Table | Column | Type | Description |
|---|---|---|---|
| `purchase_order` | `expected_date` | DATE | Already exists |
| `purchase_order` | `supplier_reliability_score` | DECIMAL(5,2) | Optional reliability |
| `purchase_order_item` | `remaining_qty` | INTEGER | Derived or cached |
| `purchase_order_item` | `committed_qty` | INTEGER | Quantity promised to future demand |
| `purchase_order_item` | `available_to_promise_qty` | INTEGER | Remaining future promise |

Formula:

```text
InboundATP = orderedQty - receivedQty - committedQty
```

Only purchase supply meeting policy can be promised:

- Purchase order is confirmed.
- Expected date is before the customer promise date.
- Supplier reliability is above threshold, if enabled.
- Item is not cancelled or rolled back.

Do not create physical batch outbound tasks before receipt.

---

## 8. Emergency Stock Correction

## 8.1 Objective

Handle the case:

> Physical stock exists, but the system has no usable stock record.

Do not solve this by allowing normal outbound transactions to create negative batch quantities.

## 8.2 Recommended flow

```text
Create emergency stock correction
    ↓
Record physical evidence and batch identity
    ↓
Warehouse supervisor review
    ↓
Optional second approver for high quantity/value
    ↓
Create or adjust inventory batch
    ↓
Write stock transaction and audit record
    ↓
Retry reservation
```

## 8.3 Proposed table: `emergency_stock_correction`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | BIGINT | No | Primary key |
| `correction_no` | VARCHAR(50) | No | Unique business number |
| `warehouse_id` | BIGINT | No | Warehouse |
| `location_id` | BIGINT | No | Physical location |
| `product_id` | BIGINT | No | Product |
| `inventory_batch_id` | BIGINT | Yes | Existing batch, if applicable |
| `batch_code` | VARCHAR(50) | No | Existing or newly created batch |
| `production_date` | DATE | Yes | Required by configured product policy |
| `expiry_date` | DATE | Yes | Required for expiry-controlled goods |
| `system_qty` | INTEGER | No | Quantity before correction |
| `counted_qty` | INTEGER | No | Physical quantity found |
| `adjustment_qty` | INTEGER | No | countedQty - systemQty |
| `reason_code` | VARCHAR(50) | No | Reason classification |
| `reason_detail` | VARCHAR(500) | No | Human explanation |
| `evidence_url` | VARCHAR(500) | Yes | Photo or document reference |
| `status` | VARCHAR(30) | No | Workflow state |
| `requested_by` | BIGINT | No | Requester |
| `requested_at` | TIMESTAMP | No | Request time |
| `reviewed_by` | BIGINT | Yes | First reviewer |
| `reviewed_at` | TIMESTAMP | Yes | First review time |
| `approved_by` | BIGINT | Yes | Second approver |
| `approved_at` | TIMESTAMP | Yes | Final approval time |
| `related_sales_order_id` | BIGINT | Yes | Order waiting for stock |
| `version` | BIGINT | No | Optimistic lock |

Recommended statuses:

```text
DRAFT
PENDING_REVIEW
PENDING_APPROVAL
APPROVED
APPLIED
REJECTED
CANCELLED
```

## 8.4 Approval policy

Examples:

```text
Low quantity and low value
  → one supervisor approval

High quantity, high value or expired-date-sensitive goods
  → warehouse supervisor + finance or operations approval
```

The applied correction must create a normal `StockTransaction` with:

```text
sourceType = EMERGENCY_CORRECTION
sourceOrderId = correction_no
quantityBefore
quantityAfter
operator
reason
```

---

## 9. API Design

## 9.1 Reservation APIs

Internal application service is preferred over exposing every reservation operation publicly.

Potential management endpoints:

```text
GET  /api/inventory/reservations?salesOrderId={id}
GET  /api/inventory/reservations/batch/{batchId}
POST /api/inventory/reservations/{id}/release
POST /api/sales-orders/{id}/reallocate
```

## 9.2 Sales approval

```text
POST /api/sales-orders/{id}/approve
```

Proposed request:

```json
{
  "comment": "Approved",
  "allocationPolicy": "FULL_ONLY",
  "requestedShipDate": "2026-06-20"
}
```

Possible responses:

```text
200 APPROVED + ALLOCATED
200 APPROVED + PARTIALLY_ALLOCATED
200 APPROVED + BACKORDERED
409 invalid commercial state
409 concurrent reservation conflict
422 policy cannot fulfill requested quantity
```

## 9.3 Emergency correction APIs

```text
POST /api/emergency-stock-corrections
GET  /api/emergency-stock-corrections/{id}
POST /api/emergency-stock-corrections/{id}/submit
POST /api/emergency-stock-corrections/{id}/review
POST /api/emergency-stock-corrections/{id}/approve
POST /api/emergency-stock-corrections/{id}/apply
POST /api/emergency-stock-corrections/{id}/reject
```

---

## 10. Concurrency Strategy

Reservation correctness is more important than allocation throughput.

Recommended approach:

1. Query candidate batches by FEFO.
2. Lock selected rows using pessimistic write locking, or perform conditional atomic updates.
3. Recalculate Available inside the transaction.
4. Create reservations.
5. Retry only known concurrency conflicts.

Conditional update example:

```sql
UPDATE inventory_batch
SET reserved_quantity = reserved_quantity + :qty,
    version = version + 1
WHERE id = :batchId
  AND quantity - reserved_quantity >= :qty
  AND version = :expectedVersion;
```

Updated row count must equal one. Otherwise, reload and retry allocation within a bounded retry policy.

Do not retry:

- Validation failures.
- Expired batches.
- Inactive batches.
- Invalid order state.
- Genuine insufficient stock.

---

## 11. Idempotency

The following commands require idempotency protection:

- Sales order creation from external platforms.
- Approval.
- Reservation.
- Reservation release.
- Outbound confirmation.
- Emergency correction application.
- Backorder wake-up.

Recommended request header:

```text
Idempotency-Key: <client-generated-key>
```

Store:

```text
key
operation
aggregate_id
request_hash
response_status
response_body
created_at
expires_at
```

Reusing a key with a different request hash must return `409 Conflict`.

---

## 12. Audit and Observability

Required audit events:

```text
ORDER_APPROVED
RESERVATION_CREATED
RESERVATION_RELEASED
RESERVATION_CONSUMED
BACKORDER_CREATED
BACKORDER_ALLOCATED
PROMISE_DATE_CHANGED
EMERGENCY_CORRECTION_REQUESTED
EMERGENCY_CORRECTION_APPROVED
EMERGENCY_CORRECTION_APPLIED
```

Recommended metrics:

- Reservation conflict count.
- Backorder quantity and age.
- Allocation success rate.
- Approval-to-allocation latency.
- Reservation-to-shipment latency.
- Emergency correction frequency.
- Inventory accuracy rate.
- In-transit promise miss rate.

---

## 13. Security Requirements

Proposed permissions:

```text
inventory:reservation:view
inventory:reservation:release
sales:approve
sales:reallocate
backorder:view
backorder:manage
inventory:emergency-correction:create
inventory:emergency-correction:review
inventory:emergency-correction:approve
inventory:emergency-correction:apply
```

Emergency correction must not rely only on `SUPER_ADMIN`. Production roles should use explicit permissions and separation of duties.

The requester must not approve their own high-risk correction.

---

## 14. Migration Strategy

### Phase 1: Reservation foundation

- Add reservation table.
- Optionally add `reserved_quantity`.
- Add Available queries.
- Create reservation and release services.
- Preserve current V4.4 `FULL_ONLY` behavior.

### Phase 2: Dual states

- Add commercial and fulfillment statuses.
- Backfill existing orders.
- Add response fields while retaining legacy status.
- Migrate outbound completion logic.

### Phase 3: Partial allocation and backorder

- Add item allocation counters.
- Add backorder table.
- Add inbound-triggered allocation.
- Add outbox processing.

### Phase 4: In-transit ATP

- Add purchase commitment quantities.
- Add promise-date calculation.
- Add supplier reliability policy.

### Phase 5: Emergency correction

- Add correction workflow and permissions.
- Integrate with stocktake and stock transactions.
- Add evidence and dual approval.

---

## 15. Test Baseline

## 15.1 Reservation tests

- Two orders cannot reserve the same available quantity.
- Reservation rollback leaves all batches unchanged.
- Cancellation releases reservation.
- Shipment consumes reservation exactly once.
- Repeated approval does not duplicate reservation.
- Optimistic or pessimistic lock conflict produces 409.

## 15.2 Dual-state tests

- Commercial approval can succeed independently of complete fulfillment when policy allows.
- Fulfillment changes do not silently alter commercial approval.
- Cancellation transitions both state machines consistently.
- A shipped order cannot be approved, edited or reserved again.

## 15.3 Backorder tests

- Partial stock creates allocation plus backorder.
- Inbound receipt wakes backorder.
- Priority and creation order are respected.
- Repeated wake-up is idempotent.
- Cancelled backorders are never allocated.

## 15.4 Emergency correction tests

- Requester cannot self-approve high-risk correction.
- Correction cannot omit required batch traceability.
- Applying correction creates inventory and audit transactions atomically.
- Repeated apply is rejected or returns the original result.
- Rejected correction never changes inventory.

## 15.5 Compatibility tests

- V4.4 full-stock approval still works.
- FEFO remains stable.
- Full-pack and loose-unit behavior remains stable.
- Batch-confirm rollback remains atomic.
- Existing JWT and RBAC behavior remains compatible.

---

## 16. Decisions

The following decisions are established for V4.5:

1. Sales order creation records intent and does not require stock.
2. Inventory commitment occurs during approval or explicit allocation.
3. Physical batch inventory must remain non-negative.
4. Backorder is the primary shortage mechanism.
5. In-transit inventory may support ATP but is not physical stock.
6. Emergency stock correction replaces forced negative shipment.
7. Commercial and fulfillment states are separate.
8. Inventory reservation must become an explicit domain object.

---

## 17. Non-Goals

V4.5 does not initially attempt to implement:

- Full transportation management.
- Supplier EDI.
- Wave picking optimization.
- Route optimization.
- Financial general ledger posting.
- Uncontrolled negative inventory.
- Automatic promise dates without configurable business policy.

---

## 18. Definition of Done

V4.5 reservation foundation is complete when:

- Available stock excludes active reservations.
- Concurrent approvals cannot over-promise stock.
- Cancellation releases reservations.
- Shipment consumes reservations.
- All reservation operations are transactional and auditable.
- V4.4 FEFO and pack-aware allocation still pass.
- Commercial and fulfillment status are both visible.
- Emergency stock mismatch has an auditable correction path.
- No normal outbound operation can produce negative batch stock.
