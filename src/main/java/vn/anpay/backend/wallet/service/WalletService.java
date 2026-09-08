package vn.anpay.backend.wallet.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.wallet.dto.WalletResponse;
import vn.anpay.backend.wallet.repository.WalletRepository;
import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository repo;
    public WalletService(WalletRepository r) {
        repo=r;

    }
    public WalletResponse getByUser(UUID userId) {
        var w=repo.findByUserId(userId).orElseThrow(()->new BusinessException("WALLET_NOT_FOUND","Không tìm thấy ví",HttpStatus.NOT_FOUND));
        return new WalletResponse(w.id,w.walletCode,w.currency,w.availableBalance,w.heldBalance,w.status);

    }

}
