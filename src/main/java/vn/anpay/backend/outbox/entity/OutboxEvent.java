package vn.anpay.backend.outbox.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity @Table(name="outbox_events") public class OutboxEvent {
    @Id
    public UUID id;
    @Column(name="aggregate_type")
    public String aggregateType;
    @Column(name="aggregate_id")
    public UUID aggregateId;
    @Column(name="event_type")
    public String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    public String payload;
    public String status;
    public int attempts;
    @Column(name="available_at")
    public Instant availableAt;
    @Column(name="processed_at")
    public Instant processedAt;
    @Column(name="last_error")
    public String lastError;
    @Column(name="created_at")
    public Instant createdAt;
    protected OutboxEvent() {

    }
    public OutboxEvent(String aggregateType,UUID aggregateId,String eventType,String payload) {
        id=UUID.randomUUID();
        this.aggregateType=aggregateType;
        this.aggregateId=aggregateId;
        this.eventType=eventType;
        this.payload=payload;
        status="PENDING";
        availableAt=Instant.now();
        createdAt=Instant.now();

    }

}
