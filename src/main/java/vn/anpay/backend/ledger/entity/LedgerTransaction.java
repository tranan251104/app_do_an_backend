package vn.anpay.backend.ledger.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity @Table(name="ledger_transactions") public class LedgerTransaction {
    @Id
    public UUID id;
    public String reference;
    public String type;
    public String status;
    public long amount;
    public long fee;
    public String description;
    @Column(name="sender_wallet_id")
    public UUID senderWalletId;
    @Column(name="receiver_wallet_id")
    public UUID receiverWalletId;
    public String provider;
    @Column(name="provider_reference")
    public String providerReference;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    public String metadata="{}";
    @Column(name="created_at")
    public Instant createdAt;
    @Column(name="updated_at")
    public Instant updatedAt;
    @Column(name="completed_at")
    public Instant completedAt;
    protected LedgerTransaction() {

    }
    public static LedgerTransaction create(String type,long amount) {
        var t=new LedgerTransaction();
        t.id=UUID.randomUUID();
        t.reference="ANP"+System.currentTimeMillis()+UUID.randomUUID().toString().substring(0,6).toUpperCase();
        t.type=type;
        t.status="PENDING";
        t.amount=amount;
        t.fee=0;
        t.createdAt=Instant.now();
        t.updatedAt=t.createdAt;
        return t;

    }

}
