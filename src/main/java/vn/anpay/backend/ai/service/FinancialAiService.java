package vn.anpay.backend.ai.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import vn.anpay.backend.ledger.entity.LedgerTransaction;
import vn.anpay.backend.ledger.repository.LedgerTransactionRepository;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialAiService {

    private final GeminiClient geminiClient;
    private final WalletRepository walletRepo;
    private final LedgerTransactionRepository ledgerRepo;
    private final UserRepository userRepo;

    public FinancialAiService(GeminiClient geminiClient, WalletRepository walletRepo,
                              LedgerTransactionRepository ledgerRepo, UserRepository userRepo) {
        this.geminiClient = geminiClient;
        this.walletRepo = walletRepo;
        this.ledgerRepo = ledgerRepo;
        this.userRepo = userRepo;
    }

    public String chat(UUID userId, String userMessage) {
        User user = userRepo.findById(userId).orElse(null);
        Wallet wallet = walletRepo.findByUserId(userId).orElse(null);

        String context = buildFinancialContext(user, wallet);
        
        String systemInstruction = "Bạn là Trợ lý Ảo Tài chính của ví điện tử AnPay, tên là AnPay AI. " +
                "Nhiệm vụ của bạn là giải đáp các thắc mắc về tài chính cá nhân, số dư, lịch sử giao dịch một cách thân thiện, ngắn gọn và chuyên nghiệp.\n\n" +
                "Dưới đây là DỮ LIỆU TÀI CHÍNH THỰC TẾ của người dùng hiện tại tính đến thời điểm hiện hành:\n" +
                context +
                "\n\nHãy dùng các thông tin trên để trả lời câu hỏi của người dùng. Trả lời bằng tiếng Việt. Nếu họ hỏi những thứ ngoài dữ liệu này (ví dụ: giao dịch từ năm ngoái), hãy xin lỗi khéo léo. Tuyệt đối KHÔNG bịa đặt số tiền hay giao dịch.";

        return geminiClient.generateContent(systemInstruction, userMessage);
    }

    private String buildFinancialContext(User user, Wallet wallet) {
        if (user == null || wallet == null) {
            return "Tài khoản hoặc ví chưa được kích hoạt.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("- Tên khách hàng: ").append(user.fullName).append("\n");
        sb.append("- Số dư khả dụng: ").append(wallet.availableBalance).append(" VND\n");
        
        // Fetch last 10 transactions
        Instant fromAt = Instant.EPOCH;
        Instant toAt = Instant.now();
        List<LedgerTransaction> txs = ledgerRepo.searchForUser(
                user.id, wallet.id, null, null, null, null,
                fromAt, toAt, PageRequest.of(0, 10)).getContent();

        if (txs.isEmpty()) {
            sb.append("- Lịch sử giao dịch: Chưa có giao dịch nào.\n");
        } else {
            sb.append("- Các giao dịch gần đây (mới nhất xếp trước):\n");
            for (LedgerTransaction tx : txs) {
                String direction = (tx.senderWalletId != null && tx.senderWalletId.equals(wallet.id)) ? "TRỪ TIỀN (Gửi đi)" : "CỘNG TIỀN (Nhận vào)";
                String desc = (tx.description != null) ? tx.description : "Không có mô tả";
                sb.append("  + [").append(tx.createdAt).append("] ")
                  .append(direction).append(" - Số tiền: ").append(tx.amount).append(" VND ")
                  .append("- Nội dung: ").append(desc).append(" ")
                  .append("(Loại: ").append(tx.type).append(", Trạng thái: ").append(tx.status).append(")\n");
            }
        }
        return sb.toString();
    }
}
