package vn.anpay.backend.auth.firebase;

import java.time.Instant;

public record FirebasePhoneIdentity(String uid, String phoneNumber, Instant authenticatedAt) {
}
