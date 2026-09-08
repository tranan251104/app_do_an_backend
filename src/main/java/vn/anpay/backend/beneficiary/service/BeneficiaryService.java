package vn.anpay.backend.beneficiary.service;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.beneficiary.dto.BeneficiaryDto;
import vn.anpay.backend.beneficiary.entity.Beneficiary;
import vn.anpay.backend.beneficiary.repository.BeneficiaryRepository;
import vn.anpay.backend.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;

@Service
public class BeneficiaryService {
    private final BeneficiaryRepository r;
    public BeneficiaryService(BeneficiaryRepository r) {
        this.r=r;

    }
    public Page<BeneficiaryDto> list(UUID uid,Pageable p) {
        return r.findByUserId(uid,p).map(this::dto);

    }
    @Transactional
    public BeneficiaryDto create(UUID uid,BeneficiaryDto d) {
        var b=new Beneficiary();
        b.id=UUID.randomUUID();
        b.userId=uid;
        copy(b,d);
        b.createdAt=Instant.now();
        b.updatedAt=b.createdAt;
        return dto(r.save(b));

    }
    @Transactional
    public BeneficiaryDto update(UUID uid,UUID id,BeneficiaryDto d) {
        var b=owned(uid,id);
        copy(b,d);
        b.updatedAt=Instant.now();
        return dto(b);

    }
    @Transactional
    public void delete(UUID uid,UUID id) {
        r.delete(owned(uid,id));

    }
    private Beneficiary owned(UUID uid,UUID id) {
        var b=r.findById(id).orElseThrow(()->new BusinessException("BENEFICIARY_NOT_FOUND","Không tìm thấy người thụ hưởng",HttpStatus.NOT_FOUND));
        if(!b.userId.equals(uid))throw new BusinessException("FORBIDDEN","Không có quyền truy cập",HttpStatus.FORBIDDEN);
        return b;

    }
    private void copy(Beneficiary b,BeneficiaryDto d) {
        b.type=d.type();
        b.bankBin=d.bankBin();
        b.bankName=d.bankName();
        b.accountNumber=d.accountNumber();
        b.accountName=d.accountName();
        b.nickname=d.nickname();

    }
    private BeneficiaryDto dto(Beneficiary b) {
        return new BeneficiaryDto(b.id,b.type,b.bankBin,b.bankName,b.accountNumber,b.accountName,b.nickname);

    }

}
