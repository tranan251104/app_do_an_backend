# API summary

Auth: `/auth/register`, `/auth/login`, `/auth/email/send-otp`, `/auth/email/resend-otp`, `/auth/email/verify-otp`, `POST /auth/phone`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password/request`, `/auth/forgot-password/verify`, `/auth/forgot-password/reset`, `/auth/change-password`.
Profile: `GET/PATCH /me`, `POST /me/phone/link`.

Phone auth contract:

- `POST /auth/login` (public): `{ "identifier": "0912345678", "password": "...", "deviceId": "..." }`. `identifier` nhận email hoặc số điện thoại Việt Nam; số điện thoại được chuẩn hóa về E.164 trước khi tra cứu. Trả `AuthResponse` khi mật khẩu đúng.
- `POST /auth/register`: đăng ký bằng điện thoại phải kèm `firebaseIdToken`. Backend lấy số từ token đã xác minh, đặt `phoneVerified=true` và lưu Firebase UID ngay khi tạo user. Trường `phone`, nếu gửi, chỉ được dùng để kiểm tra khớp với token.
- `POST /me/phone/link` (Bearer AnPay token): `{ "firebaseIdToken": "...", "currentPassword": "..." }`. Trả số E.164 đã liên kết. `409 PHONE_ALREADY_IN_USE` nếu số/UID Firebase đã thuộc user khác.
- `POST /auth/phone` (public): `{ "firebaseIdToken": "...", "deviceId": "..." }`. Trả cùng `AuthResponse` với đăng nhập email.
- Email OTP (public): gọi `POST /auth/email/send-otp` với `{ "email": "..." }`, sau đó `POST /auth/email/verify-otp` với `challengeId`, `email`, `otp`, `deviceId`. Email chưa có tài khoản phải gửi thêm `fullName` và `password`; email đã có tài khoản có thể bỏ hai trường này để đăng nhập. OTP đúng sẽ đặt `emailVerified=true` và trả `AuthResponse`. Gửi lại bằng `POST /auth/email/resend-otp` với challenge cũ và email.
- Backend không tin một trường `phone` rời; số đã xác minh luôn được lấy từ Firebase ID token.
- Email OTP đăng ký (public): gọi `POST /auth/email/send-otp` với `{ "email": "..." }`, sau đó `POST /auth/email/verify-otp` với `challengeId`, `email`, `otp`, `deviceId`, `fullName` và `password`. OTP đúng sẽ tạo tài khoản, đặt `emailVerified=true` và trả `AuthResponse`. Gửi lại bằng `POST /auth/email/resend-otp` với challenge cũ và email. Cả ba bước đều trả `409 ACCOUNT_EXISTS` nếu email đã được đăng ký trước đó.
- Demo only: khi `DEMO_PHONE_OTP_ENABLED=true`, `POST /auth/phone/send-otp`, `/resend-otp`, `/verify-otp` mô phỏng luồng OTP đăng ký. `/verify-otp` phải nhận `fullName` và `password`; backend lưu tên đã trim và BCrypt hash của đúng mật khẩu này. Cả ba bước trả `409 ACCOUNT_EXISTS` nếu số điện thoại đã được đăng ký trước đó. Delivery `response` trả `demoOtp`; delivery `fcm` đẩy mã tới token app. Cơ chế này không xác minh quyền sở hữu số/SIM và phải tắt ở production.
Wallet: `GET /wallets/me`, `GET /wallets/me/qr`.
Transfer: `POST /transfers/prepare`, `POST /transfers/{id}/confirm`, `GET /transfers/{id}`.

Transfer retry contract (internal and external): `Idempotency-Key` is scoped to
the authenticated user and transfer operation. Reusing it with different request
content returns `409 IDEMPOTENCY_CONFLICT`; identical retries return the original
transaction. Confirm/resend require the sender even when the transaction is already
completed. A wallet frozen/closed after prepare causes confirm to return
`WALLET_UNAVAILABLE`. See [transaction and migration details](transfer-safety.md).

PayOS: `POST /payments/topups/payos`, `GET /payments/topups/{id}`, `POST /webhooks/payos`.
History: `GET /transactions`, `GET /transactions/{id}`.
Beneficiary CRUD: `/beneficiaries`.
Notification: `/notifications`, `/{id}/read`, `/read-all`.
QR: `POST /qr/resolve`.
Catalog: `/catalog/providers`, `/catalog/products`, `/promotions`, `/partners`.
