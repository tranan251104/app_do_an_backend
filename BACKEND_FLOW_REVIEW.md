# Review backend AnPay — 27/09/2026

Phạm vi: chỉ backend trong thư mục này. Báo cáo này thay thế phần kết luận trộn frontend/backend ở FLOW_REVIEW_2026-09-27.md. Không coi thiếu tích hợp phía client là lỗi backend.

## Các phát hiện

### 1. [P1] Thu hồi refresh-token family bị rollback

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:128–135`.

refresh() có @Transactional. Khi token đã bị thu hồi, hàm gọi revokeFamily rồi ném BusinessException, là RuntimeException. Transaction mặc định rollback, bao gồm lệnh revokeFamily. Ví dụ R1 đã đổi lấy R2: dùng lại R1 nhận lỗi REUSE nhưng R2 vẫn dùng được. Cần lưu việc thu hồi trước khi trả lỗi bằng transaction boundary phù hợp. Test bắt buộc: truy vấn trạng thái family sau request reuse và thử dùng R2.

### 2. [P1] Khóa tài khoản không chặn phiên đang có

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:128–140`; `src/main/java/vn/anpay/backend/common/security/AuthenticationFilter.java:27–31`.

Login kiểm tra users.status nhưng refresh không kiểm tra. AuthenticationFilter chỉ xác minh JWT. User bị chuyển khỏi ACTIVE vẫn có thể dùng refresh token để lấy access token mới; các API như transfer chỉ kiểm tra wallet.status. Nếu ví vẫn ACTIVE, khóa user không chặn chuyển tiền. Cần kiểm tra trạng thái tài khoản nhất quán khi cấp/nhận phiên. Test: khóa user đã có ví và phiên, xác nhận refresh và API tiền đều bị từ chối theo chính sách.

### 3. [P1] Endpoint đăng ký trực tiếp bỏ qua xác minh email

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:61–116`.

/auth/register public nhận email + mật khẩu và cấp JWT mà không cần email OTP. Người gọi có thể đăng ký trước email của người khác, khiến người sở hữu thật bị ACCOUNT_EXISTS ở cả luồng OTP. emailVerified chưa được dùng để giới hạn login/cấp ví. Nếu backend yêu cầu xác minh email như luồng EmailOtpAuthService, cần buộc register dùng bằng chứng xác minh tương đương hoặc chỉ tạo tài khoản chưa được phép sử dụng cho đến khi xác minh. Nếu cố ý hỗ trợ đăng ký email chưa xác minh, cần cơ chế cho chủ email hoàn tất xác minh/khôi phục và quy định rõ các quyền bị giới hạn.

### 4. [P2] Hai refresh đồng thời có thể cùng thành công

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:130–140`; `src/main/java/vn/anpay/backend/auth/repository/RefreshTokenRepository.java:10`.

findByTokenHash không khóa hàng và RefreshToken không có @Version. Hai request có thể cùng đọc revokedAt=null rồi tạo hai token con. Cần khóa hàng hoặc phép cập nhật có điều kiện nguyên tử. Test: dùng barrier để gửi cùng refresh token từ hai request đồng thời, kiểm tra chỉ một lần rotation được chấp nhận.

### 5. [P2] User chỉ có số điện thoại không nhận được OTP chuyển nội bộ

Vị trí: `src/main/java/vn/anpay/backend/transfer/service/TransferService.java:86–110`, `:129`.

Backend hỗ trợ tạo user không có email. Prepare vẫn tạo OTP_REQUIRED nhưng chỉ gửi OTP nếu email khác null; không có SMS/push fallback trong luồng này. Resend cũng không gửi được. Cần kiểm tra kênh nhận OTP trước khi tạo giao dịch hoặc triển khai kênh cho user phone-only. forgotRequest cũng bỏ qua user không có email, nên luồng khôi phục mật khẩu phone-only chưa được hỗ trợ.

### 6. [P2] Payment hết hạn vẫn còn PENDING trong DB

Vị trí: `src/main/java/vn/anpay/backend/payment/service/MockPaymentService.java:251–263`.

expireIfNeeded sửa payment thành EXPIRED và ledger transaction thành FAILED rồi ném BusinessException. details/confirm đều @Transactional, nên hai thay đổi bị rollback. API báo hết hạn nhưng lịch sử DB vẫn PENDING. Cần commit trạng thái hết hạn trước khi trả lỗi hoặc trả kết quả trạng thái thay vì exception trong transaction. Test kiểm tra DB sau gọi details/confirm với payment quá hạn.

### 7. [P2] Idempotency top-up không kiểm tra số tiền

Vị trí: `src/main/java/vn/anpay/backend/payment/service/MockPaymentService.java:94–99`.

Cùng user và key, lần đầu amount=10.000, lần sau amount=100.000 vẫn trả payment cũ thay vì IDEMPOTENCY_CONFLICT. Hàm chỉ kiểm tra provider. Cần so sánh amount và các trường quyết định giao dịch trước khi coi request là replay, tương tự transfer prepare.

### 8. [P2] Tạo top-up đồng thời dễ xung đột order code

Vị trí: `src/main/java/vn/anpay/backend/payment/service/MockPaymentService.java:326–329`.

Order code bắt đầu từ timestamp giây và dùng findByOrderCode để dò số trống. Hai request độc lập cùng giây có thể cùng chọn một số trước khi request kia commit. Unique constraint ngăn dữ liệu trùng nhưng một request sẽ lỗi; không có cơ chế retry. Cùng idempotency key cũng chưa được serialize như transfer. Dùng sequence/cơ chế cấp mã nguyên tử và khóa theo user/key; test nhiều request cùng thời điểm.

### 9. [P2] Reset token chưa thực sự dùng một lần khi có cạnh tranh

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:170–178`.

forgotReset dùng Redis GET rồi DELETE sau khi sửa password entity. Hai request có thể cùng GET thành công và ghi hai mật khẩu khác nhau. Redis DELETE cũng không rollback nếu DB commit lỗi, khiến user mất quyền retry. Cần tiêu thụ token nguyên tử và giải quyết transaction giữa Redis/DB; lưu quyền reset có khóa trong DB là một phương án. Test hai request cùng token và lỗi DB lúc commit.

### 10. [P2] Chuyển nội bộ không áp dụng giới hạn số lần gửi lại OTP

Vị trí: `src/main/java/vn/anpay/backend/transfer/service/TransferService.java:115–126`.

resend chỉ kiểm tra thời gian chờ, đánh dấu mã cũ consumed rồi issue challenge mới. Không kiểm tra tổng số lần resend; mỗi challenge mới lại có attemptCount=0. Cấu hình max-resends và OtpRateLimitService hiện chỉ được áp dụng cho chuyển ngân hàng, không cho chuyển nội bộ. Cần đếm resend theo giao dịch, không theo challenge mới; kiểm thử vượt giới hạn và OTP đã bị khóa.

### 11. [P2] Yêu cầu OTP quên mật khẩu không có giới hạn ở backend

Vị trí: `src/main/java/vn/anpay/backend/auth/service/AuthService.java:144–153`.

Mỗi request public cho tài khoản có email lập tức tạo challenge và thêm outbox; không kiểm tra cooldown, số lần gửi hay quota. Người gọi có thể tạo nhiều email và thay mã được forgotVerify lựa chọn liên tục, làm người dùng khó hoàn tất reset. Cần giới hạn theo tài khoản và nguồn request; không thấy lớp rate limiter toàn cục trong source. Chưa đánh giá rate limit có thể tồn tại ở gateway bên ngoài.

## Luồng nghiệp vụ và các cơ chế đã có

| Luồng | Trình tự backend | Nhận xét |
|---|---|---|
| Email OTP | send → outbox/SMTP → verify → user + session | Có hash mã, hạn dùng, số lần sai, resend; xem đường bypass ở mục 3 |
| Điện thoại | Xác minh Firebase ID token → liên kết/login; nhánh đăng ký demo riêng | Có kiểm tra provider/identity và trạng thái user trong PhoneAuthService; demo không chứng minh sở hữu SIM |
| Đăng nhập/phiên | password login → access/refresh → rotation/logout | Xem mục 1, 2, 4; đổi mật khẩu hiện không thu hồi refresh token, cần chốt chính sách đăng xuất các thiết bị |
| Khôi phục mật khẩu | request → OTP → reset token Redis → đổi password + revoke refresh | Xem mục 9, 11 và giới hạn phone-only ở mục 5 |
| Cấp số ví | status/check → claim hoặc random → wallet + ledger account + notification | Có khóa user và unique constraint; đây là onboarding chủ đích, không phải lỗi thiếu tạo ví trong register |
| Chuyển nội bộ | prepare → OTP → confirm → balances + ledger + notification/outbox | Khóa transaction, kiểm tra chủ sở hữu trước replay, khóa ví theo thứ tự UUID, kiểm tra lại số dư và trạng thái ví; mục 5, 10 còn thiếu |
| Chuyển ngân hàng | prepare → OTP → MockPayoutProvider → debit + clearing ledger | Hiện là simulator; không kết luận có gửi tiền ngân hàng thật. Nếu thay bằng provider thật, phải thiết kế xử lý side effect ngoài DB/đối soát trước khi sử dụng |
| Nạp tiền | tạo mock intent → capability-token checkout → confirm/cancel/expire | Confirm khóa payment và ví, có chống cộng tiền lặp theo PAID; xem mục 6–8 |
| Lịch sử | Lọc thời gian/loại/hướng/trạng thái → kiểm tra user/ví | Có giới hạn khoảng thời gian, quyền xem transaction; chưa có phát hiện chắc chắn mới trong lần đọc này |
| Người thụ hưởng | create/list/update/delete theo user | Update/delete kiểm tra ownership |
| Thông báo | Lưu notification → outbox → FCM; list/read/count theo user | Có ownership, retention; retry push có thể gửi lặp cho thiết bị đã nhận, source đã ghi nhận giới hạn này |
| Outbox | Khóa batch SKIP LOCKED → gửi → PROCESSED/retry/FAILED | OTP delivery copy chỉ bị xóa sau commit. SMTP/FCM vẫn có thể giao lặp nếu gửi thành công nhưng commit lỗi |
| Catalog/AI | API đọc danh mục; AI lấy dữ liệu user/ví và giao dịch | Chưa thấy nghiệp vụ backend đặt vé/thanh toán hóa đơn hoàn chỉnh; AI dùng provider ngoài, chưa kiểm thử runtime |

## Giới hạn kiểm chứng

- Chỉ review source; không sửa code nghiệp vụ hay dữ liệu.
- Đã thử Maven offline và online với JDK 21. Chưa tới bước compile/test: thiếu parent Spring Boot 4.1.1 trong cache và Maven Central bị chặn với `Permission denied: getsockopt`.
- Kết quả 66 test pass trong TEST_STATUS.md là báo cáo cũ, chưa xác nhận lại ở trạng thái hiện tại.
- Chưa kiểm thử DB/migrations, SMTP, FCM, Firebase và HTTP endpoints bằng môi trường chạy thật. Các kịch bản nêu trên là suy luận từ code và transaction boundary, không được mô tả là test đã pass/fail.
- Hướng ưu tiên: sửa phiên/xác thực (1–4), sau đó trạng thái và concurrency nạp tiền (6–8), rồi OTP/reset (5, 9–11); thêm integration test tại các transaction boundary này.
