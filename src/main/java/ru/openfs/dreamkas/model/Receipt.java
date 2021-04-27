package ru.openfs.dreamkas.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Receipt {
    public String externalId;
    public int deviceId;
    public String type = "SALE";
    public int timeout = 5;
    public String taxMode = "SIMPLE_WO";
    public List<Position> positions;
    public List<Payment> payments;
    public Attributes attributes;
    public Total total;

    public static class Position {
        public String name;
        public String type = "SERVICE";
        public int quantity = 1;
        public long price;
        public long priceSum;
        public String tax = "NDS_NO_TAX";
        public long taxSum = 0;
    }

    public static class Payment {
        public long sum;
        public String type = "CASHLESS";
    }

    public static class Total {
        public long priceSum;
    }

    public static class Attributes {
        public String email;
        public String phone;
    }

}
