# Transfer safety fixes

## OTP and transaction boundaries

`OtpService` throws `OtpAttemptException` only after an incorrect code increments
`attemptCount`. Internal/external confirm and password-reset verification commit
this specific exception. They still roll back other business/runtime failures.
Do not broaden this rule to `BusinessException`: a later failure must undo OTP
consumption, wallet balances, ledger entries and notifications together.

Confirm locks the ledger transaction before reading its transfer request. Resend
uses the same lock order, so it cannot replace an OTP while confirm is using it.
Internal transfers then lock both wallets in UUID order. External transfers lock
the sender wallet. Wallet entities are first loaded under those locks so the JPA
persistence context cannot retain an earlier balance/status snapshot.

Only the sender can confirm/resend, including a retry of a completed transaction.
Confirm rechecks `ACTIVE` after locking wallets. A frozen/closed sender or internal
receiver prevents the transfer before OTP consumption or balance changes.

## Idempotency contract

The identity of a prepare request is `(user_id, recipient_type, idempotency_key)`.
`ANPAY` and `BANK` are separate operations. Different users may safely use the same
UUID key. A repeated request with matching content returns the original result.
Different content returns HTTP 409 with `IDEMPOTENCY_CONFLICT`.

The original payload is already persisted: transaction amount, recipient reference,
note and external bank/account metadata. Compare these values directly rather than
maintaining a second hash that could drift from them. Wallet codes and external
bank/account fields are trimmed consistently with creation; notes are compared
exactly, including null versus an empty string.

`TransferIdempotencyLock` takes a PostgreSQL transaction-scoped advisory lock for
the user/operation/key before lookup. This also serializes simultaneous first
requests when no row exists. The database unique index provides the final guard.
Hash collisions only cause extra waiting; they do not equate two requests. This
avoids taking a user-row lock that could conflict with foreign-key checks while
another transaction holds wallet locks.

## Outbox delivery

Workers select up to 20 eligible rows using `FOR UPDATE SKIP LOCKED` and hold the
locks through delivery and the status update. Another instance skips those rows.
Rollback releases the rows for retry. This implementation holds a DB connection
and row locks during SMTP/push calls, so provider timeouts must stay bounded.

The staged Redis OTP is deleted only after the `PROCESSED` update commits. A DB
failure after SMTP accepts an email leaves the code available for retry. Redis
cleanup failure falls back to its short TTL.

Delivery remains **at least once**: a process crash after the provider accepts a
message but before the DB commit can cause duplicate email/push. This is not an
exactly-once guarantee. `app.outbox.enabled=false` disables the scheduled worker
for tests; the default is enabled.

## Database upgrade

Flyway V9 adds `transfer_requests.user_id`, backfills it from each transaction's
sender wallet, makes it required, and replaces the global idempotency index with
the scoped unique index. Existing requests/keys are retained.

Stop old backend instances before deploying this version and applying V9 through
Flyway. The old binary does not populate the required `user_id` and must not run
alongside the new schema. The automated migration test upgrades a populated V8
schema inside a disposable PostgreSQL container; it does not migrate a live DB.

## Verification

Run with JDK 21 and Docker available:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd test
```

`CoreMoneyFlowIntegrationTest` covers persisted OTP attempts/lockout, owner checks
on completed retries, frozen/closed wallets, rollback after ledger writes, payload
conflicts, keys shared across users/operations, simultaneous prepares/confirms,
competing transfers with insufficient balance, outbox batch exclusion, rollback
recovery, SMTP retry and V8-to-V9 backfill. SMTP, push and payout are mocked; the
database and Redis are real Testcontainers services.

External payout is still a simulator. These fixes do not turn a local DB
transaction into a distributed transaction with a real bank.
