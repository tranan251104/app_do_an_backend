package vn.anpay.backend.wallet.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="wallets") public class Wallet {
    @Id
    public UUID id;
    @Column(name="user_id",nullable=false,unique=true)
    public UUID userId;
    @Column(name="wallet_code",nullable=false,unique=true)
    public String walletCode;
    public String currency;
    @Column(name="available_balance",nullable=false)
    public long availableBalance;
    @Column(name="held_balance",nullable=false)
    public long heldBalance;
    public String status;
    @Version
    public long version;
    @Column(name="created_at")
    public Instant createdAt;
    @Column(name="updated_at")
    public Instant updatedAt;
    protected Wallet() {

    }
    public Wallet(UUID userId,String code) {
        this.id=UUID.randomUUID();
        this.userId=userId;
        this.walletCode=code;
        this.currency="VND";
        this.status="ACTIVE";
        this.createdAt=Instant.now();
        this.updatedAt=this.createdAt;

    }

}
