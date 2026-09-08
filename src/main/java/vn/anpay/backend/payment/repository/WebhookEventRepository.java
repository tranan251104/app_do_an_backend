package vn.anpay.backend.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.payment.entity.WebhookEvent;
import java.util.*;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent,UUID> {
    boolean existsByProviderAndProviderEventKey(String provider,String key);

}
