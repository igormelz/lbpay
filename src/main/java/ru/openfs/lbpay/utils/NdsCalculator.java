package ru.openfs.lbpay.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class NdsCalculator {
    private static final long NDS_5_NUMERATOR = 5;
    private static final long NDS_5_DENOMINATOR = 105;
    private static final long NDS_20_NUMERATOR = 20;
    private static final long NDS_20_DENOMINATOR = 120;

    public static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final LocalDateTime NEW_YEAR = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    public static final Instant NEW_YEAR_INSTANT = NEW_YEAR.atZone(ZoneId.of("+03:00")).toInstant();

    private NdsCalculator() {
    }

    public static int extractNDS5(int price) {
        return (int) ((price * NDS_5_NUMERATOR + NDS_5_DENOMINATOR / 2) / NDS_5_DENOMINATOR);
    }

    public static int extractNDS20(int price) {
        return (int) ((price * NDS_20_NUMERATOR + NDS_20_DENOMINATOR / 2) / NDS_20_DENOMINATOR);
    }

    public static boolean needNds(String payDate) {
        return LocalDateTime.parse(payDate, BILL_DATE_FMT).isAfter(NEW_YEAR);
    }

    public static boolean needNds(Instant payDate) {
        return payDate.isAfter(NEW_YEAR_INSTANT);
    }
}
