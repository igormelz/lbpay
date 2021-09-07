package ru.openfs.lbpay.sberonline.model;

public enum SberOnlineCode {
    ERR(-1, "Внутренняя ошибка"), // don't define in sber proto, need 300
    OK(0, "Успешное завершение операции"), // success
    TMP_ERR(1, "Временная ошибка. Повторите запрос позже"), // temp unavailable
    WRONG_ACTION(2, "Неизвестный тип запроса"), // no check no payment
    ACCOUNT_NOT_FOUND(3, "Абонент не найден"), // ACCOUNT NOT FOUND
    ACCOUNT_WRONG_FORMAT(4, "Неверный формат идентификатора Плательщика"), //
    ACCOUNT_INACTIVE(5, "Счет Плательщика не активен"), //
    WRONG_TRX_FORMAT(6, "Неверное значение идентификатора транзакции"), //
    PAYMENT_NOT_AVAILABLE(7, "Прием платежа запрещен по техническим причинам"), //
    PAY_TRX_DUPLICATE(8, "Дублирование транзакции"), //
    PAY_AMOUNT_ERROR(9, "Неверная сумма платежа"), //
    PAY_AMOUNT_TOO_SMALL(10, "Сумма слишком мала"), //
    PAY_AMOUNT_TOO_BIG(11, "Сумма слишком велика"), //
    WRONG_FORMAT_DATE(12, "Неверное значение даты"), //
    BACKEND_ERR(300, "Внутренняя ошибка Организации");

    private final int code;
    private final String msg;

    SberOnlineCode(int code, String msg) {
       this.code = code;
       this.msg = msg;
    }

    public String getMsg() {
        return this.msg;
    }

    public int getCode() {
        return this.code;
    }
}
