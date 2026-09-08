package vn.anpay.backend.qr.dto;
public record QrResponse(String type,String walletCode,String displayName,boolean canTransfer,String uri) {

}
