package ru.openfs.lbpay;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import ru.openfs.lbpay.model.AuditOrder;
import ru.openfs.lbpay.model.AuditRecord;

@CheckedTemplate
public class Templates {
    public static native TemplateInstance order(AuditOrder order);

    public static native TemplateInstance pay_status(String icon, String status, String orderNumber, String message);

    public static native TemplateInstance receipt(AuditRecord receipt);

    public static native TemplateInstance notifyError(String message);
}
