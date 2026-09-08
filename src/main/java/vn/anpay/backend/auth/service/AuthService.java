package vn.anpay.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.auth.dto.*;
import vn.anpay.backend.auth.entity.RefreshToken;
import vn.anpay.backend.auth.firebase.FirebasePhoneIdentity;
import vn.anpay.backend.auth.firebase.FirebasePhoneTokenVerifier;
import vn.anpay.backend.auth.repository.RefreshTokenRepository;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.common.security.JwtService;
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.OtpService;
import vn.anpay.backend.outbox.service.OtpDeliveryService;
import vn.anpay.backend.outbox.service.OutboxService;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final long refreshDays;
    private final SecureRandom random=new SecureRandom();
    private final OtpService otp;
    private final OtpRepository otpRepo;
    private final OtpDeliveryService otpDelivery;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final PhoneNumberNormalizer phoneNumbers;
    private final FirebasePhoneTokenVerifier firebaseTokens;
    public AuthService(UserRepository users,RefreshTokenRepository refreshRepo,PasswordEncoder passwords,JwtService jwt,@Value("${app.jwt.refresh-days}") long refreshDays,OtpService otp,OtpRepository otpRepo,OtpDeliveryService otpDelivery,OutboxService outbox,ObjectMapper mapper,StringRedisTemplate redis,PhoneNumberNormalizer phoneNumbers,FirebasePhoneTokenVerifier firebaseTokens) {
        this.users=users;
        this.refreshRepo=refreshRepo;
        this.passwords=passwords;
        this.jwt=jwt;
        this.refreshDays=refreshDays;
        this.otp=otp;
        this.otpRepo=otpRepo;
        this.otpDelivery=otpDelivery;
        this.outbox=outbox;
        this.mapper=mapper;
        this.redis=redis;
        this.phoneNumbers=phoneNumbers;
        this.firebaseTokens=firebaseTokens;

    }
    @Transactional
    public AuthResponse register(RegisterRequest r) {
        String email=norm(r.email());
        if(email!=null)email=email.toLowerCase(Locale.ROOT);
        String requestedPhone=phoneNumbers.normalizeOptional(r.phone());
        String firebaseIdToken=norm(r.firebaseIdToken());
        FirebasePhoneIdentity phoneIdentity=null;
        String phone=null;

        if(firebaseIdToken!=null) {
            phoneIdentity=firebaseTokens.verify(firebaseIdToken);
            phone=phoneNumbers.normalize(phoneIdentity.phoneNumber());
            if(requestedPhone!=null&&!requestedPhone.equals(phone)) {
                throw new BusinessException(
                        "PHONE_TOKEN_MISMATCH",
                        "Số điện thoại không khớp với phiên xác minh Firebase",
                        HttpStatus.BAD_REQUEST
                );
            }
        } else if(requestedPhone!=null) {
            throw new BusinessException(
                    "PHONE_VERIFICATION_REQUIRED",
                    "Cần xác minh OTP trước khi đăng ký bằng số điện thoại",
                    HttpStatus.BAD_REQUEST
            );
        }

        if(email==null&&phone==null) throw new BusinessException("CONTACT_REQUIRED","Cần email hoặc số điện thoại",HttpStatus.BAD_REQUEST);
        if(email!=null&&users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(
                    "ACCOUNT_EXISTS",
                    "Email đã được đăng ký trước đó. Vui lòng sử dụng email khác",
                    HttpStatus.CONFLICT
            );
        }
        if((phone!=null&&users.existsByPhoneNormalized(phone))
                || (phoneIdentity!=null&&users.findByFirebasePhoneUid(phoneIdentity.uid()).isPresent())) {
            throw new BusinessException(
                    "ACCOUNT_EXISTS",
                    "Số điện thoại đã được đăng ký trước đó. Vui lòng sử dụng số điện thoại khác",
                    HttpStatus.CONFLICT
            );
        }

        var u=new User(UUID.randomUUID(),email,phone,passwords.encode(r.password()),r.fullName().trim());
        if(phoneIdentity!=null) {
            Instant verifiedAt=Instant.now();
            u.firebasePhoneUid=phoneIdentity.uid();
            u.phoneVerified=true;
            u.phoneVerifiedAt=verifiedAt;
            u.updatedAt=verifiedAt;
        }
        u=users.save(u);
        // Wallet creation is intentionally deferred until the onboarding step.
        // The short-lived access token returned here authorizes the user to claim
        // a beautiful account number or ask the backend for a random one.
        return tokens(u.id,null,null);

    }
    public AuthResponse login(LoginRequest r) {
        String id=r.identifier().trim();
        var u=(id.contains("@")?users.findByEmailIgnoreCase(id.toLowerCase(Locale.ROOT)):users.findByPhoneNormalized(phoneNumbers.normalize(id))).orElse(null);
        if(u==null||!passwords.matches(r.password(),u.passwordHash)) throw new BusinessException("INVALID_CREDENTIALS","Thông tin đăng nhập không hợp lệ",HttpStatus.UNAUTHORIZED);
        if(!"ACTIVE".equals(u.status)) throw new BusinessException("ACCOUNT_UNAVAILABLE","Tài khoản không khả dụng",HttpStatus.FORBIDDEN);
        return tokens(u.id,null,r.deviceId());

    }
    @Transactional
    public AuthResponse refresh(RefreshRequest r) {
        String h=hash(r.refreshToken());
        var old=refreshRepo.findByTokenHash(h).orElse(null);
        if(old==null) throw new BusinessException("INVALID_REFRESH_TOKEN","Refresh token không hợp lệ",HttpStatus.UNAUTHORIZED);
        if(old.revokedAt!=null) {
            refreshRepo.revokeFamily(old.familyId,Instant.now());
            throw new BusinessException("REFRESH_TOKEN_REUSE","Phiên đăng nhập đã bị thu hồi",HttpStatus.UNAUTHORIZED);

        }
        if(old.expiresAt.isBefore(Instant.now())) throw new BusinessException("REFRESH_TOKEN_EXPIRED","Phiên đăng nhập đã hết hạn",HttpStatus.UNAUTHORIZED);
        old.revokedAt=Instant.now();
        refreshRepo.save(old);
        return tokens(old.userId,old.familyId,r.deviceId());

    }
    @Transactional
    public void forgotRequest(ForgotPasswordRequest r) {
        var u=findIdentifier(r.identifier()).orElse(null);
        if(u==null||u.email==null)return;
        var issued=otp.issue(u.id,"PASSWORD_RESET",u.id);
        otpDelivery.stage(issued.challenge().id,issued.plaintextCode());
        try {
            outbox.add("USER",u.id,"SEND_OTP_EMAIL",mapper.writeValueAsString(Map.of("challengeId",issued.challenge().id.toString(),"email",u.email,"purpose","PASSWORD_RESET")));

        } catch(Exception ignored) {

        }

    }
    @Transactional(noRollbackFor = vn.anpay.backend.otp.service.OtpAttemptException.class)
    public String forgotVerify(ForgotPasswordVerifyRequest r) {
        var u=findIdentifier(r.identifier()).orElseThrow(()->new BusinessException("OTP_INVALID","OTP không chính xác hoặc đã hết hạn",HttpStatus.BAD_REQUEST));
        var list=otpRepo.findAll().stream().filter(o->o.userId.equals(u.id)&&"PASSWORD_RESET".equals(o.purpose)&&o.consumedAt==null).sorted(Comparator.comparing((vn.anpay.backend.otp.entity.OtpChallenge o)->o.createdAt).reversed()).toList();
        if(list.isEmpty())throw new BusinessException("OTP_INVALID","OTP không chính xác hoặc đã hết hạn",HttpStatus.BAD_REQUEST);
        var ch=otpRepo.lockById(list.get(0).id).orElseThrow();
        otp.verifyLocked(ch,"PASSWORD_RESET",u.id,r.otp());
        String reset=randomToken();
        redis.opsForValue().set("pwdreset:"+hash(reset),u.id.toString(),Duration.ofMinutes(10));
        return reset;

    }
    @Transactional
    public void forgotReset(ForgotPasswordResetRequest r) {
        String key="pwdreset:"+hash(r.resetToken());
        String uid=redis.opsForValue().get(key);
        if(uid==null)throw new BusinessException("RESET_TOKEN_INVALID","Phiên đặt lại mật khẩu không hợp lệ hoặc đã hết hạn",HttpStatus.BAD_REQUEST);
        var u=users.findById(UUID.fromString(uid)).orElseThrow();
        u.passwordHash=passwords.encode(r.newPassword());
        u.updatedAt=Instant.now();
        redis.delete(key);
        refreshRepo.findAll().stream().filter(t->t.userId.equals(u.id)&&t.revokedAt==null).forEach(t->t.revokedAt=Instant.now());

    }
    @Transactional
    public void changePassword(UUID uid,ChangePasswordRequest r) {
        var u=users.findById(uid).orElseThrow();
        if(!passwords.matches(r.currentPassword(),u.passwordHash))throw new BusinessException("INVALID_CREDENTIALS","Mật khẩu hiện tại không đúng",HttpStatus.BAD_REQUEST);
        u.passwordHash=passwords.encode(r.newPassword());
        u.updatedAt=Instant.now();

    }
    private Optional<User> findIdentifier(String value) {
        String id=value.trim();
        return id.contains("@")?users.findByEmailIgnoreCase(id.toLowerCase(Locale.ROOT)):users.findByPhoneNormalized(phoneNumbers.normalize(id));

    }
    @Transactional
    public void logout(String refreshToken) {
        if(refreshToken==null)return;
        refreshRepo.findByTokenHash(hash(refreshToken)).ifPresent(t-> {
            t.revokedAt=Instant.now();refreshRepo.save(t);
        });

    }
    private AuthResponse tokens(UUID uid,UUID family,String device) {
        String access=jwt.issueAccess(uid);
        String raw=randomToken();
        UUID f=family==null?UUID.randomUUID():family;
        refreshRepo.save(new RefreshToken(uid,f,hash(raw),Instant.now().plus(Duration.ofDays(refreshDays)),device));
        return new AuthResponse(access,raw,"Bearer",900);

    }
    @Transactional
    public AuthResponse issueSession(UUID userId,String deviceId) {
        return tokens(userId,null,deviceId);

    }
    private String randomToken() {
        byte[] b=new byte[48];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);

    }
    private String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));

        } catch(Exception e) {
            throw new IllegalStateException(e);

        }

    }
    private String norm(String s) {
        return s==null||s.isBlank()?null:s.trim();

    }

}
