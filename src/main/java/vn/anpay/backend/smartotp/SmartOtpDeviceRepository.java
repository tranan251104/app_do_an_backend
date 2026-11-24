package vn.anpay.backend.smartotp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SmartOtpDeviceRepository extends JpaRepository<SmartOtpDevice,UUID> {
    Optional<SmartOtpDevice> findFirstByUserIdAndStatusOrderByActivatedAtDesc(UUID userId,String status);
    Optional<SmartOtpDevice> findByIdAndUserId(UUID id,UUID userId);
    List<SmartOtpDevice> findByUserIdAndStatus(UUID userId,String status);
}
