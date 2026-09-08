# AnPay Backend

Backend Java 21 / Spring Boot 4.1.x cho AnPay. Flutter chỉ là client: không được tự cập nhật số dư, tự xác nhận OTP, hoặc giữ payOS secret.

## Cảnh báo bảo mật trước khi chạy production

Source Flutter gốc được cung cấp có credential payOS từng hard-code trong `lib/navigator/payOS/payos_service.dart`. Hãy coi các khóa đó là **đã lộ**, thu hồi/rotate trên payOS trước khi dùng backend này. Không copy secret cũ sang repository. `.env.example` chỉ chứa tên biến.

## Local

1. IntelliJ IDEA: chọn JDK 21.
2. Bật Docker Desktop.
3. `docker compose up -d`
4. Copy `src/main/resources/application-local.yml.example` thành `src/main/resources/application-local.yml` nếu muốn override local.
5. Thiết lập `JWT_SECRET` >= 32 byte. Nếu test payOS thật, thiết lập `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, URL return/cancel/webhook bằng environment.
6. Chạy `vn.anpay.backend.AnpayBackendApplication`.
7. Health: `GET http://localhost:8080/actuator/health`.
8. Windows test: `.\mvnw.cmd test`
9. Flutter Android emulator dùng `http://10.0.2.2:8080/api/v1`.

## Đăng nhập bằng số điện thoại

Backend giữ email và số điện thoại như hai phương thức đăng nhập của cùng một `users.id`:

- Đăng nhập bằng mật khẩu: gọi `POST /api/v1/auth/login` với `identifier` là email hoặc số điện thoại Việt Nam và trường `password`. Backend chuẩn hóa số điện thoại về E.164 trước khi tra cứu.
- Đăng nhập bằng OTP Firebase: dùng luồng dưới đây để không gửi mật khẩu.

1. App dùng Firebase Phone Auth gửi/xác nhận OTP SMS và lấy Firebase ID token.
2. User đang đăng nhập bằng email gọi `POST /api/v1/me/phone/link` với ID token và mật khẩu hiện tại.
3. Backend xác minh chữ ký/claim của token, lấy `phone_number` từ token, chuẩn hóa về `+84...` và liên kết vào đúng user hiện tại.
4. Những lần sau app gọi `POST /api/v1/auth/phone`; backend đổi Firebase ID token mới xác minh thành access/refresh token AnPay.

Thiết lập `FIREBASE_PROJECT_ID` đúng với project của Flutter. ID token phải đến từ provider `phone` và có `auth_time` không quá `FIREBASE_PHONE_MAX_AUTH_AGE_SECONDS` (mặc định 10 phút). Backend dùng public certificate của Firebase để xác minh nên không cần service-account JSON cho luồng auth; service-account vẫn cần riêng nếu bật FCM push.

### OTP số điện thoại mô phỏng (chỉ dùng demo)

Khi chưa dùng Firebase Phone Auth/SMS, bật `DEMO_PHONE_OTP_ENABLED=true` để dùng luồng mô phỏng:

1. `POST /api/v1/auth/phone/send-otp` với `phoneNumber` và `fcmToken` tùy chọn.
2. `POST /api/v1/auth/phone/verify-otp` với `challengeId`, `phoneNumber`, `otp`, `deviceId`, `fullName` và `password` để tạo tài khoản và nhận JWT.
3. `POST /api/v1/auth/phone/resend-otp` với challenge cũ nếu cần gửi lại.

`DEMO_PHONE_OTP_DELIVERY=response` trả mã trong trường `demoOtp`. Đổi thành `fcm` để gửi notification qua FCM; khi đó phải bật `FCM_ENABLED=true`, cấu hình `FIREBASE_CREDENTIALS_PATH`, và gửi `fcmToken` của app. OTP có hạn 3 phút, chờ 60 giây mới gửi lại, tối đa 3 lần gửi lại, 5 lần nhập sai và chỉ dùng một lần.

Luồng này **không xác minh người dùng sở hữu SIM/số điện thoại đã nhập**. Vì vậy feature mặc định tắt và phải luôn tắt ở production. Tài khoản tự tạo bởi luồng demo có `phoneVerified=false`, dù vẫn nhận JWT để trình diễn ứng dụng.

Nếu số điện thoại đã tồn tại trong bảng `users`, API gửi/gửi lại/xác minh OTP đều trả HTTP `409`, mã `ACCOUNT_EXISTS` và thông báo `Số điện thoại đã được đăng ký trước đó. Vui lòng sử dụng số điện thoại khác`. Đăng nhập số điện thoại đã có tài khoản tiếp tục dùng `POST /api/v1/auth/phone` với Firebase ID token hoặc `POST /api/v1/auth/login` với mật khẩu.

Mail local xem tại `http://localhost:8025` (Mailpit).

## Đăng ký bằng OTP email

1. `POST /api/v1/auth/email/send-otp` với `{ "email": "user@example.com" }` để nhận `challengeId`; email được gửi bất đồng bộ qua SMTP/outbox.
2. `POST /api/v1/auth/email/verify-otp` với `challengeId`, `email`, `otp`, `deviceId`, `fullName` và `password` để tạo user.
3. `POST /api/v1/auth/email/resend-otp` với `challengeId` và `email` để thay challenge cũ bằng mã mới sau thời gian chờ.

OTP email có hạn 5 phút, chờ 60 giây mới gửi lại, tối đa 3 lần gửi lại và 5 lần nhập sai. Sau khi xác minh thành công, backend đánh dấu `emailVerified=true` và trả access/refresh token AnPay.

Nếu email đã tồn tại trong bảng `users`, API gửi/gửi lại/xác minh OTP đều trả HTTP `409`, mã `ACCOUNT_EXISTS` và thông báo `Email đã được đăng ký trước đó. Vui lòng sử dụng email khác`. API `/auth/register` áp dụng cùng quy tắc cho cả email và số điện thoại.

## Luồng tiền

- Register tạo user + wallet + ledger account trong cùng transaction.
- Transfer prepare lấy sender từ JWT, resolve `wallet_code`, tạo `OTP_REQUIRED`, OTP hash trong PostgreSQL; plaintext OTP chỉ được giữ ngắn hạn trong Redis để worker gửi email.
- Transfer confirm khóa 2 ví theo thứ tự UUID cố định, verify+consume OTP và chuyển tiền trong cùng transaction; tạo 2 ledger entries tổng bằng 0.
- Top-up payOS chỉ cộng tiền trong webhook đã được SDK payOS verify. `returnUrl` không thay đổi balance.
- Duplicate confirm/webhook là idempotent.

## Trạng thái kiểm thử của gói này

Ngày 31/08/2026, `.\mvnw.cmd test` đã chạy bằng JDK 21 với PostgreSQL/Redis Testcontainers: **41 test pass, 0 failure, 0 error**. Kết quả này gồm unit test auth/OTP và chống trùng email/số điện thoại, cùng 6 integration test luồng tiền; vẫn cần cấu hình SMTP thật và kiểm thử end-to-end client trước khi triển khai production.
