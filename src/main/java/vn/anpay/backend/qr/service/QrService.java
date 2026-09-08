package vn.anpay.backend.qr.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.qr.dto.QrResponse;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.repository.WalletRepository;
import java.util.*;

@Service
public class QrService {
    private final WalletRepository wallets;
    private final UserRepository users;
    public QrService(WalletRepository w,UserRepository u) {
        wallets=w;
        users=u;

    }
    public QrResponse mine(UUID uid) {
        var w=wallets.findByUserId(uid).orElseThrow();
        var u=users.findById(uid).orElseThrow();
        return dto(w.walletCode,u.fullName,true);

    }
    public QrResponse resolve(String input) {
        String code=input;
        if(input.contains("walletCode=")) {
            code=input.substring(input.indexOf("walletCode=")+11).split("[&#]")[0];

        }
        var w=wallets.findByWalletCode(code).orElseThrow(()->new BusinessException("QR_NOT_FOUND","Không tìm thấy ví AnPay",HttpStatus.NOT_FOUND));
        var u=users.findById(w.userId).orElseThrow();
        return dto(w.walletCode,u.fullName,"ACTIVE".equals(w.status));

    }
    private QrResponse dto(String code,String name,boolean can) {
        String[] p=name.trim().split("\\s+");
        String masked=p[0].toUpperCase(Locale.ROOT)+" *** "+p[p.length-1].substring(0,1).toUpperCase(Locale.ROOT);
        return new QrResponse("ANPAY",code,masked,can,"anpay://pay?walletCode="+code);

    }

}
