# AnPay backend fix report

This revision intentionally focuses on the backend core issues found during review.

## Fixed

- PostgreSQL JSONB mapping for all String-backed JSONB entity fields:
  - `LedgerTransaction.metadata`
  - `OutboxEvent.payload`
  - `PaymentIntent.rawMetadata`
  - `CatalogProvider.metadata`
  - `CatalogProduct.metadata`
  - `Promotion.metadata`
- Added integration-test coverage for:
  - registration creates user, wallet and ledger account;
  - duplicate phone registration is rejected;
  - transfer prepare persists and is idempotent;
  - successful transfer creates exactly two balanced ledger entries;
  - duplicate confirm does not transfer money twice;
  - insufficient balance is rejected.
- The transfer test also exercises the outbox JSONB payload on `TRANSFER_COMPLETED`.

## Not changed in this revision

- Flutter application source in `C:\\Users\\DELL\\app_do_an`.
- Phone/SMS OTP delivery.
- Google/Firebase token exchange.
- CCCD/profile expansion.
- Remaining service-payment/travel/lottery/financial-goal APIs.

## Test execution status in this environment

`./mvnw compile/test` could not be executed here because this runtime cannot resolve `repo.maven.apache.org`.
Run on the development machine (with Docker Desktop running):

```powershell
.\\mvnw.cmd clean test
```

The integration test uses Testcontainers PostgreSQL 17 and Redis 7, so Docker must be running.
