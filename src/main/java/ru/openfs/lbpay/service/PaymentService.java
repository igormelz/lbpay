package ru.openfs.lbpay.service;

import java.util.Optional;

import io.quarkus.logging.Log;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.openfs.lbpay.client.LbCoreSoapClient;
import ru.openfs.lbpay.exception.PaymentException;
import ru.openfs.lbpay.model.ReceiptCustomerInfo;
import ru.openfs.lbpay.model.ReceiptOrder;

@ApplicationScoped
public class PaymentService {

    @Inject
    LbCoreSoapClient lbSoapService;

    @Inject
    EventBus eventBus;

    protected String getSession() {
        return Optional.ofNullable(lbSoapService.login())
                .orElseThrow(() -> new PaymentException("no lb session"));
    }

    /**
     * process payment by orderNumber
     * 
     * @param orderNumber the orderNumber to paid
     * @param mdOrder     the orderNumber reference id
     */
    public void processPayment(Long orderNumber, String mdOrder) {
        Log.debugf("start payment orderNumber:[%d]", orderNumber);

        final String sessionId = getSession();

        try {
            var order = lbSoapService.findOrderNumber(sessionId, orderNumber)
                    .orElseThrow(() -> new PaymentException("not found orderNumber:[" + orderNumber + "]"));

            if (order.getStatus() != 0) {
                Log.warnf("orderNumber:[" + orderNumber + "] was paid at " + order.getPaydate());
            } else {
                lbSoapService.confirmPrePayment(sessionId, orderNumber, order.getAmount(), mdOrder);

                var acct = lbSoapService.findAccountByAgrmId(sessionId, order.getAgrmid()).orElseThrow();
                var agrm = acct.getAgreements().stream().filter(a -> a.getAgrmid() == order.getAgrmid()).findFirst()
                        .orElseThrow();
                Log.infof("paid orderNumber:[%d], account:[%s], amount:[%.2f]",
                        orderNumber, agrm.getNumber(), order.getAmount());

                eventBus.send("register-receipt", new ReceiptOrder(
                    order.getAmount(), String.valueOf(orderNumber), agrm.getNumber(), mdOrder,
                    new ReceiptCustomerInfo(acct.getAccount().getEmail(), acct.getAccount().getMobile())));
            }

        } catch (RuntimeException e) {
            eventBus.send("notify-error", String.format("orderNumber:[%d] deposited: %s", orderNumber, e.getMessage()));
            throw new PaymentException(e.getMessage());
        } finally {
            lbSoapService.logout(sessionId);
        }
    }

    /**
     * process decline orderNumber
     * 
     * @param orderNumber the orderNumber to decline
     */
    public void processDecline(long orderNumber) {
        Log.debugf("start decline orderNumber: [%d]", orderNumber);
        final String sessionId = getSession();

        try {
            var order = lbSoapService.findOrderNumber(sessionId, orderNumber)
                    .orElseThrow(() -> new PaymentException("not found orderNumber:[" + orderNumber + "]"));

            if (order.getStatus() != 0) {
                Log.warnf("orderNumber:[" + orderNumber + "] was declined at " + order.getPaydate());
            } else {
                lbSoapService.cancelPrePayment(sessionId, orderNumber);
                Log.infof("declined orderNumber:[%d]", orderNumber);
            }

        } catch (RuntimeException e) {
            eventBus.send("notify-error", String.format("orderNumber:[%d] declined: %s", orderNumber, e.getMessage()));
            throw new PaymentException(e.getMessage());
        } finally {
            lbSoapService.logout(sessionId);
        }
    }
}
