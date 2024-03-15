package ru.openfs.lbpay.components;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import ru.openfs.lbpay.model.PrePayment;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@CheckedTemplate
public class Templates {
    public static native TemplateInstance order(PrePayment order);

    public static native TemplateInstance pay_status(String icon, String status, String orderNumber, String message);

    public static native TemplateInstance receiptOperationStatus(DreamkasOperation receipt);

    public static native TemplateInstance notifyError(String message);
}
