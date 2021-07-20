package ru.openfs.audit;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Operation {
    @Id public String mdOrder;
    public String orderNumber;
    public String account;
    public String email;
    public String phone;
    public Double amount;
    public String operId;
    public String status;
    public String createAt;
}
