package ru.openfs.lbpay.audit;

import java.time.LocalDateTime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class AuditRecord {
    public String mdOrder;
    public String orderNumber;
    public String account;
    public String phone;
    public String email;
    public Double amount;
    public String operId;
    public String status;
    public LocalDateTime createAt;

    public AuditRecord() {
        //default constructor
    }

    public AuditRecord(String mdOrder, String orderNumber, String account, Double amount, String phone, String email,
                       LocalDateTime createAt, String operId, String status) {
        this.mdOrder = mdOrder;
        this.account = account;
        this.orderNumber = orderNumber;
        this.email = email;
        this.phone = phone;
        this.amount = amount;
        this.createAt = createAt;
        this.operId = operId;
        this.status = status;
    }

}
