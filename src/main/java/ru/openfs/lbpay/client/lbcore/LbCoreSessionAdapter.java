/*
 * Copyright 2021-2024 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.lbpay.client.lbcore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

import api3.CancelPrePayment;
import api3.ConfirmPrePayment;
import api3.ExternCheckPayment;
import api3.ExternCheckPaymentResponse;
import api3.ExternPayment;
import api3.GetAgreementsBrief;
import api3.GetAgreementsBriefResponse;
import api3.GetExternAccount;
import api3.GetExternAccountResponse;
import api3.GetPrePayments;
import api3.GetPrePaymentsResponse;
import api3.GetRecommendedPayment;
import api3.InsPrePayment;
import api3.Login;
import api3.Logout;
import api3.SoapAccountFull;
import api3.SoapAgreementBrief;
import api3.SoapFilter;
import api3.SoapPayment;
import api3.SoapPaymentFull;
import api3.SoapPrePayment;
import io.quarkus.logging.Log;

/**
 * lbcore session wrapper and message adapter
 */
public class LbCoreSessionAdapter implements AutoCloseable {
    public static final Long AGRM_NUM = 5L;
    public static final Long AGRM_ID = 11L;
    private static final DateTimeFormatter BILL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final String sessionId;
    private final LbCoreSoapClient client;

    public LbCoreSessionAdapter(LbCoreSoapClient client) {
        this.client = client;
        this.sessionId = getSession();
    }

    private String getSession() {
        Login soapRequest = new Login();
        soapRequest.setLogin(client.login);
        soapRequest.setPass(client.pass);
        try {
            return client.callService(soapRequest, null).getString("sessionId");
        } catch (RuntimeException e) {
            Log.error(e.getMessage());
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        if (sessionId != null)
            client.callService(new Logout(), sessionId);
    }

    /**
     * test if account is active
     * 
     * @param  account
     * @return         true is active or false is inactive
     */
    public boolean isActiveAccount(String account) {
        return findAgreementByAccount(account).isPresent();
    }

    /**
     * find active account agreementId
     * 
     * @param  account
     * @return         agreementId or empty
     */
    public Optional<Long> getAgreementIdByAccount(String account) {
        return findAgreementByAccount(account).map(agrm -> agrm.getAgrmid());
    }

    private Optional<SoapAgreementBrief> findAgreementByAccount(String account) {
        SoapFilter filter = new SoapFilter();
        filter.setAgrmnum(account);
        GetAgreementsBrief request = new GetAgreementsBrief();
        request.setFlt(filter);
        return client.getMandatoryResponse(request, sessionId, GetAgreementsBriefResponse.class)
                .getRet().stream()
                .filter(agrm -> agrm.getNumber().equalsIgnoreCase(account))
                .filter(agrm -> agrm.getClosedon().isBlank())
                .findFirst();
    }

    /**
     * create payment order
     * 
     * @param  agreementId
     * @param  amount
     * @return             orderNumber
     */
    public long createOrderNumber(long agreementId, double amount) {
        SoapPrePayment data = new SoapPrePayment();
        data.setAgrmid(agreementId);
        data.setAmount(amount);
        data.setCurname("RUR");
        data.setComment("form checkout");
        data.setPaydate(BILL_DATE_FMT.format(LocalDateTime.now()));

        InsPrePayment request = new InsPrePayment();
        request.setVal(data);

        return client.callService(request, sessionId).getJsonObject("data").getLong("ret");
    }

    /**
     * find account info by account number
     * 
     * @param  account
     * @return
     */
    public Optional<SoapAccountFull> findAccountInfo(String account) {
        try {
            GetExternAccount request = new GetExternAccount();
            request.setId(AGRM_NUM);
            request.setStr(account);
            return client.getMandatoryResponse(request, sessionId, GetExternAccountResponse.class)
                    .getRet().stream().findFirst();
        } catch (RuntimeException e) {
            if (e.getMessage().startsWith("Agreement not found."))
                return Optional.empty();
            throw e;
        }
    }

    /**
     * get recomended payment for agreement id
     * 
     * @param  agreementId
     * @return
     */
    public double getRecomendedPayment(long agreementId) {
        GetRecommendedPayment recPaymentReq = new GetRecommendedPayment();
        recPaymentReq.setId(agreementId);
        return client.callService(recPaymentReq, sessionId).getJsonObject("data").getDouble("ret");
    }

    /**
     * process payment
     * 
     * @param  externalId
     * @param  account
     * @param  amount
     * @param  payDate
     * @return
     */
    public long sberOnlinePayment(String externalId, String account, double amount, String payDate) {
        SoapPayment payment = new SoapPayment();
        payment.setPaydate(payDate);
        payment.setAmount(amount);
        payment.setComment("SberOnline");
        payment.setModperson(0L);
        payment.setCurrid(0L);
        payment.setReceipt(externalId);
        payment.setClassid(0L);

        ExternPayment request = new ExternPayment();
        request.setId(5);
        request.setStr(account);
        request.setVal(payment);
        request.setOperid(0L);
        request.setNotexists(1L);

        return client.callService(request, sessionId).getJsonObject("data").getLong("ret");
    }

    /**
     * get payment by externalId
     * 
     * @param  externalId
     * @return
     */
    public Optional<SoapPaymentFull> findPayment(String externalId) {
        Objects.requireNonNull(externalId);
        try {
            ExternCheckPayment checkReq = new ExternCheckPayment();
            checkReq.setReceipt(externalId);
            return client.getMandatoryResponse(checkReq, sessionId, ExternCheckPaymentResponse.class)
                    .getRet().stream().findFirst();
        } catch (RuntimeException e) {
            if (e.getMessage().matches("Payment with receipt = " + externalId + " and mod_person = \\d+ not found")) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * find PrePayment order by orderNumber
     * 
     * @param  orderNumber
     * @return             prePayment order
     */
    public Optional<SoapPrePayment> findPrePaymentByOrderNumber(long orderNumber) {
        SoapFilter filter = new SoapFilter();
        filter.setRecordid(orderNumber);
        GetPrePayments request = new GetPrePayments();
        request.setFlt(filter);
        return client.getMandatoryResponse(request, sessionId, GetPrePaymentsResponse.class).getRet()
                .stream().findFirst();
    }

    /**
     * process payment order by orderNumber, amount, externalId
     * 
     * @param orderNumber
     * @param amount
     * @param externalId
     */
    public void confirmPrePayment(long orderNumber, double amount, String externalId) {
        ConfirmPrePayment request = new ConfirmPrePayment();
        request.setRecordid(orderNumber);
        request.setAmount(amount);
        request.setReceipt(externalId);
        request.setPaydate(BILL_DATE_FMT.format(LocalDateTime.now()));
        client.callService(request, sessionId);
    }

    /**
     * process canceling payment order
     * 
     * @param orderNumber
     */
    public void cancelPrePayment(long orderNumber) {
        CancelPrePayment request = new CancelPrePayment();
        request.setRecordid(orderNumber);
        request.setCanceldate(BILL_DATE_FMT.format(LocalDateTime.now()));
        client.callService(request, sessionId);
    }

    /**
     * find account by agreement id
     * 
     * @param  agreementId
     * @return             account
     */
    public Optional<SoapAccountFull> findAccountByAgreementId(long agreementId) {
        GetExternAccount request = new GetExternAccount();
        request.setId(AGRM_ID);
        request.setStr(Long.toString(agreementId));
        return client.getMandatoryResponse(request, sessionId, GetExternAccountResponse.class).getRet()
                .stream().findFirst();
    }
}
