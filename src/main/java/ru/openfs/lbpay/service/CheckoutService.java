package ru.openfs.lbpay.service;

public interface CheckoutService {
    boolean processCheckAccount(String account);
    String processCheckout(String account, Double amount);
}
