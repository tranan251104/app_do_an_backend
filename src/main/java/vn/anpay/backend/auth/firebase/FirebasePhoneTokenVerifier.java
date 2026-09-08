package vn.anpay.backend.auth.firebase;

public interface FirebasePhoneTokenVerifier {
    FirebasePhoneIdentity verify(String idToken);
}
