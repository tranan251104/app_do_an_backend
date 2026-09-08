package vn.anpay.backend.ledger.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="ledger_entries") public class LedgerEntry {
    @Id
    public UUID id;
    @Column(name="ledger_transaction_id")
    public UUID transactionId;
    @Column(name="ledger_account_id")
    public UUID accountId;
    @Column(name="delta_amount")
    public long deltaAmount;
    @Column(name="balance_after")
    public Long balanceAfter;
    @Column(name="created_at")
    public Instant createdAt;
    protected LedgerEntry() {

    }
    public LedgerEntry(UUID tx,UUID account,long delta,long after) {
        id=UUID.randomUUID();
        transactionId=tx;
        accountId=account;
        deltaAmount=delta;
        balanceAfter=after;
        createdAt=Instant.now();

    }

}
