package ru.openfs.lbpay.bot;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import ru.openfs.lbpay.model.AuditOrder;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@CheckedTemplate
public class Templates {
    public static native TemplateInstance order(AuditOrder order);

    public static native TemplateInstance pay_status(String icon, String status, String orderNumber, String message);

    public static native TemplateInstance receiptOperationStatus(DreamkasOperation receipt);

    public static native TemplateInstance notifyError(String message);
}
