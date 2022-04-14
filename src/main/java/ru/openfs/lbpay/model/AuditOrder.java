package ru.openfs.lbpay.model;

import java.time.LocalDateTime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AuditOrder {
    public int orderNumber;
    public LocalDateTime payDate;
    public String comments;
    public int diff;
    
    public AuditOrder() {
        // default constructor
    }

    public AuditOrder(int orderNumber, LocalDateTime payDate, String comments, int diff) {
        this.orderNumber = orderNumber;
        this.payDate = payDate;
        this.comments = comments;
        this.diff = diff;
    }
}
