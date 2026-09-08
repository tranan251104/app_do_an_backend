package vn.anpay.backend.payment.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="webhook_events",uniqueConstraints=@UniqueConstraint(columnNames= {
    "provider","provider_event_key"
})) public class WebhookEvent {
    @Id
    public UUID id;
    public String provider;
    @Column(name="provider_event_key")
    public String providerEventKey;
    @Column(name="signature_valid")
    public boolean signatureValid;
    @Column(name="payload_hash")
    public String payloadHash;
    @Column(name="processed_at")
    public Instant processedAt;
    @Column(name="processing_result")
    public String processingResult;
    @Column(name="created_at")
    public Instant createdAt;
    public WebhookEvent() {

    }

}
