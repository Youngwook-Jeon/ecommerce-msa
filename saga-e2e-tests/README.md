# Saga CDC E2E tests

Run from the backend reactor root with Docker available:

```sh
./mvnw -pl saga-e2e-tests -am -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CustomerRefundCdcEndToEndTest -Dfailsafe.failIfNoSpecifiedTests=false verify
```

`CustomerRefundCdcEndToEndTest` runs in Failsafe after the executable Order and
Payment jars are packaged. Maven also prepares the Debezium scripting/Groovy
jars required by the production result connectors. Do not run it in the earlier
Surefire `test` phase. For an IDE run, prepare the artifacts first:

```sh
./mvnw -pl saga-e2e-tests -am -DskipTests pre-integration-test
```

The test uses PostgreSQL 18 logical replication, Kafka, Debezium Connect, and
separate containers running the real Order and Payment applications. It reads
the three customer-refund connector definitions from `deployment/docker/connectors`
and overrides only test database/slot/publication settings, topic prefixes, and
heartbeat interval. Production result filtering and payload conversion are retained.

Coverage:

- A confirmed Order and completed Payment are seeded as the purchase prerequisite.
  An authenticated `POST /orders/{orderId}/refunds` creates the request and outbox
  through the real application service. JWT validation and Order's client-credentials
  call to Payment's batch status API use a test-only identity provider.
- Order outbox → Debezium → `customer.refund.requested` → Payment consumer →
  the existing stub PSP → successful processing ledger/result outbox → Debezium →
  `customer.refund.completed` → Order consumer → `GET /refunds/{refundId}`.
- A second case seeds a late-failure **Payment outbox fixture** after successful
  completion and verifies the production `customer.refund.failed` relay, metadata,
  and `FAILED_AFTER_COMPLETION` state. This does not test Stripe failure detection
  or webhook receipt; those are covered separately in service tests.
- Fresh snapshot connectors redeliver the persisted request and completion events.
  Kafka group offsets must confirm consumption before assertions verify one stub
  refund call, one processing ledger entry, one completion outbox entry, and no
  regression from late failure to completion. No test Kafka producer is used.
- Order remains `CONFIRMED`; customer processing does not write the compensation
  refund ledger or customer DLT queues.

This suite does not call real Stripe, start Keycloak/Product/Redis/Gateway, exercise
inventory or delivery, or introduce customer refund operational replay/close APIs.
The existing `RefundOutboxToPaymentConsumerIT` remains the compensation CDC test.
