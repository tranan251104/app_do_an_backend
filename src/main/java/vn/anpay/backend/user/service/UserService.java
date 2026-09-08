package vn.anpay.backend.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.user.dto.*;
import vn.anpay.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository repo;
    public UserService(UserRepository r) {
        repo=r;

    }
    public MeResponse me(UUID id) {
        var u=repo.findById(id).orElseThrow(()->new BusinessException("USER_NOT_FOUND","Không tìm thấy người dùng",HttpStatus.NOT_FOUND));
        return new MeResponse(u.id,u.email,u.phone,u.phoneVerified,u.phoneVerifiedAt,u.fullName,u.dateOfBirth,u.gender,u.address,u.status);

    }
    @Transactional
    public MeResponse update(UUID id,UpdateMeRequest r) {
        var u=repo.findById(id).orElseThrow();
        if(r.fullName()!=null&&!r.fullName().isBlank())u.fullName=r.fullName().trim();
        if(r.dateOfBirth()!=null)u.dateOfBirth=r.dateOfBirth();
        if(r.gender()!=null)u.gender=r.gender();
        if(r.address()!=null)u.address=r.address();
        u.updatedAt=Instant.now();
        return me(id);

    }

}
