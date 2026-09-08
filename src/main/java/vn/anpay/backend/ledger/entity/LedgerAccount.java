package vn.anpay.backend.ledger.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="ledger_accounts") public class LedgerAccount {
    @Id
    public UUID id;
    public String code;
    @Column(name="owner_type")
    public String ownerType;
    @Column(name="owner_id")
    public UUID ownerId;
    public String currency;
    public String status;
    protected LedgerAccount() {

    }
    public LedgerAccount(String code,String ownerType,UUID ownerId) {
        id=UUID.randomUUID();
        this.code=code;
        this.ownerType=ownerType;
        this.ownerId=ownerId;
        currency="VND";
        status="ACTIVE";

    }

}
