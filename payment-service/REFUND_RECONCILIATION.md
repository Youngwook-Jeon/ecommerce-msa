# Customer refund reconciliation reviews

`ProviderRefundReconciliationExecutor` distinguishes a known PSP failure from an
unconfirmed or rejected operation:

- A returned `FAILED` refund state follows the customer-refund failure outbox path.
  Initial failures do not enter the review queue. A failure observed after success
  also enters the review queue with `review_reason = CONFIRMED_LATE_FAILURE`.
  Original uncertain-outcome diagnostics are retained; confirmed failure reason
  and observation time are stored separately.
- `PaymentRefundRejectedException` (including `PaymentRefundNeedsReviewException`)
  during customer reconciliation is persisted in `customer_refund_reviews` as
  `ESCALATED`. The first diagnostic record is retained on duplicate observations.
- Transient PSP lookup and database failures remain eligible for reconciliation.
  If saving the review fails, the request remains eligible as well.

This table is separate from `payment_refund_compensation_dlts`. An escalated
customer claim is excluded from subsequent automatic reconciliation batches and
customer refund execution checks the review marker before accessing the PSP.
Already-running provider calls are not canceled by escalation.

Escalation does **not** change `provider_refund_state`, write a successful
processing ledger, or emit `CUSTOMER_REFUND_FAILED`: the provider may already have
refunded the payment. Order's customer refund therefore remains `REQUESTED` until
its outcome is established.

Operators must inspect the PSP refund/payment and the stored diagnostic before
choosing a follow-up. A review-query API and audited manual replay/close workflow
are not introduced by this change; do not simply delete a review row to retry an
old, unknown PSP operation with an expired idempotency key.

Multiple workers may already hold the same batch item. Queue insertion is
idempotent, but escalation is not a distributed cancellation barrier. The
existing stable PSP key and idempotent result ledgers still apply.

## Kafka DLT ingestion

Payment consumes `customer.refund.requested.DLT`; Order consumes both
`customer.refund.completed.DLT` and `customer.refund.failed.DLT`. Each service
stores records in its own `customer_refund_dlts` table, separate from compensation
queues. Raw values (including malformed messages), keys, original Kafka location
and exception headers are retained. `(dlt_topic, dlt_partition, dlt_offset)` makes
redelivery idempotent even when a business ID is missing. No payment foreign key
is required: a request may fail before a claim or payment exists.

The offset is acknowledged only after the DB transaction commits. Storage failures
retry without discarding the record or publishing a DLT-of-DLT. A backlog during
a DB outage is intentional. Raw payloads are never logged; future operational APIs
must restrict access and define payload retention. Query/replay/close APIs and
their audit workflows remain deferred.

## Refund observation and late failure correction

Register `refund.created`, `refund.updated`, and `refund.failed` with Stripe.
Verified refund notifications go into `provider_refund_webhook_inbox` before HTTP
success. Processing claims are leased and reclaimed after expiry; stale workers
cannot acknowledge another worker's lease. Unknown refund IDs remain in the inbox
for retries, then `ESCALATED`, including notifications arriving before the claim
has stored its PSP refund ID. This new inbox does not yet have an operational API.

The observer matches the exact PSP refund ID and PaymentIntent association; it
never calls refund creation. A signed failure is authoritative, while other
notifications GET the current PSP refund. Webhooks and customer reconciliation
use the same short result transaction: claim state/timestamps, successful history,
result outbox and late-failure review commit together. `FAILED` is absorbing for
a customer PSP refund, so stale success/pending observations cannot revive it.

Successful processing history is never deleted. Order's `COMPLETED` means PSP
success was observed, not an irrevocable settlement guarantee. Later failure
produces `FAILED_AFTER_COMPLETION`, preserving `completedAt` and `failedAt` in the
customer query response. Order status and stock are untouched. No automatic second
refund or new PSP idempotency key is generated.

The single-refund result protocol uses precedence versions: success `1`, failure
`2` (including initial failure). Failure includes `failed_after_completion` and
observation timestamps. Order stores the last applied version with a version/status
CAS and ignores older or duplicate results. A late-failure correction can apply
before completion delivery; a delayed completion/replay cannot undo it. Legacy
events with no version map to the corresponding precedence version. Existing CDC
connectors unwrap all row columns, so these additional fields need no new topics.

An optional GET-only recent-success safety net can be enabled through
`payment-service.recent-refund-reconciliation.enabled`. Defaults: disabled,
7-day lookback, 6-hour per-record check interval, 100 records per minute. The batch
and lookback bound provider traffic; they are not an exhaustive audit or a financial
finality guarantee. Primary detection remains Stripe webhooks. Long-term operational
recovery outside the window remains manual.
