package ru.openfs.lbpay.service;

public interface CheckoutService {
    boolean isActiveAccount(String account);
    String processCheckout(String account, Double amount);
}
