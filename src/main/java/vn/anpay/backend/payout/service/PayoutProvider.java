package vn.anpay.backend.payout.service;
public interface PayoutProvider {
    PayoutResult submit(PayoutCommand command);
    record PayoutCommand(String reference,long amount,String bankBin,String accountNumber,String description) {

    }
    record PayoutResult(boolean success,String providerReference,String message) {

    }

}
