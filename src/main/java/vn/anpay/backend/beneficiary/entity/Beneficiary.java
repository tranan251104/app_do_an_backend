package vn.anpay.backend.beneficiary.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="beneficiaries") public class Beneficiary {
    @Id
    public UUID id;
    @Column(name="user_id")public UUID userId;
    public String type;
    @Column(name="bank_bin")public String bankBin;
    @Column(name="bank_name")public String bankName;
    @Column(name="account_number")public String accountNumber;
    @Column(name="account_name")public String accountName;
    public String nickname;
    @Column(name="created_at")public Instant createdAt;
    @Column(name="updated_at")public Instant updatedAt;
    public Beneficiary() {

    }

}
