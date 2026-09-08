package vn.anpay.backend.user.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="users") public class User {
    @Id
    public UUID id;
    @Column(name="firebase_uid")
    public String firebaseUid;
    public String email;
    public String phone;
    @Column(name="phone_normalized")
    public String phoneNormalized;
    @Column(name="firebase_phone_uid")
    public String firebasePhoneUid;
    @Column(name="password_hash",nullable=false)
    public String passwordHash;
    @Column(name="full_name",nullable=false)
    public String fullName;
    @Column(name="date_of_birth")
    public LocalDate dateOfBirth;
    public String gender;
    public String address;
    @Column(name="status",nullable=false)
    public String status;
    @Column(name="email_verified",nullable=false)
    public boolean emailVerified;
    @Column(name="phone_verified",nullable=false)
    public boolean phoneVerified;
    @Column(name="phone_verified_at")
    public Instant phoneVerifiedAt;
    @Column(name="created_at",nullable=false)
    public Instant createdAt;
    @Column(name="updated_at",nullable=false)
    public Instant updatedAt;
    @Version
    public long version;
    protected User() {

    }
    public User(UUID id,String email,String phone,String passwordHash,String fullName) {
        this.id=id;
        this.email=email;
        this.phone=phone;
        this.phoneNormalized=phone;
        this.passwordHash=passwordHash;
        this.fullName=fullName;
        this.status="ACTIVE";
        this.createdAt=Instant.now();
        this.updatedAt=this.createdAt;

    }

}
