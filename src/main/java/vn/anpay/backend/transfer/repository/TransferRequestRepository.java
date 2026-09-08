package vn.anpay.backend.transfer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.transfer.entity.TransferRequest;
import java.util.*;

public interface TransferRequestRepository extends JpaRepository<TransferRequest,UUID> {
    Optional<TransferRequest> findByUserIdAndRecipientTypeAndIdempotencyKey(UUID userId, String recipientType, UUID key);
    Optional<TransferRequest> findByTransactionId(UUID tx);

}
