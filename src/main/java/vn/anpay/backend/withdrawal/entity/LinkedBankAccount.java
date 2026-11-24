package vn.anpay.backend.withdrawal.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "linked_bank_accounts")
public class LinkedBankAccount {
    @Id public UUID id;
    @Column(name = "user_id", nullable = false) public UUID userId;
    @Column(name = "bank_bin", nullable = false) public String bankBin;
    @Column(name = "bank_name", nullable = false) public String bankName;
    @Column(name = "account_number", nullable = false) public String accountNumber;
    @Column(name = "account_name", nullable = false) public String accountName;
    @Column(nullable = false) public String status;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;

    protected LinkedBankAccount() { }

    public LinkedBankAccount(UUID userId, String bankBin, String bankName,
                             String accountNumber, String accountName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.bankBin = bankBin;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.status = "DEMO_LINKED";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
}
