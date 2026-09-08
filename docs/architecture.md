# Architecture

Flutter -> REST `/api/v1` -> Spring Security JWT -> feature services -> PostgreSQL/Flyway.
Redis dùng cho dữ liệu OTP delivery ngắn hạn/rate-limit; Mailpit là SMTP local. Outbox tách side effect email khỏi transaction tiền.

Double-entry ledger: mỗi transaction `COMPLETED` phải có entry cân bằng. Chuyển nội bộ là user sender `-amount`, user receiver `+amount`; top-up payOS là clearing `-amount`, user `+amount`. Ledger entry của transaction completed bị DB trigger chặn UPDATE/DELETE.
