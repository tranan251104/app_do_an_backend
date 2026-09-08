package vn.anpay.backend.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.notification.entity.PushDevice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {
    Optional<PushDevice> findByFcmToken(String fcmToken);
    List<PushDevice> findByUserIdAndEnabledTrue(UUID userId);
}
