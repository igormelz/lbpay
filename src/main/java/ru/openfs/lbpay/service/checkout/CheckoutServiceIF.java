package ru.openfs.lbpay.service.checkout;

public interface CheckoutServiceIF {
    boolean isActiveAccount(String account);
    String processCheckout(String account, Double amount);
}
