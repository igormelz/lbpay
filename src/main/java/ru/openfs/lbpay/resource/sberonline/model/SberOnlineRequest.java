package ru.openfs.lbpay.resource.sberonline.model;

public record SberOnlineRequest(Boolean isCheckOperation, String account, Double amount, String payId, String payDate) {

}
