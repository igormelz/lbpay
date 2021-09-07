package ru.openfs.lbpay.model;

import javax.xml.bind.annotation.XmlRootElement;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@XmlRootElement(name = "response")
public class SberOnlineMessage {
    public int CODE;
    public String MESSAGE;
    public String FIO;
    public String ADDRESS;
    public Double BALANCE;
    public String INFO;
    public String REG_DATE;
    public Double AMOUNT;
    public Double SUM;
    public Double REC_SUM;
    public Long EXT_ID;

    public SberOnlineMessage() {}

    public SberOnlineMessage(SberOnlineCode code) {
        this.CODE = code.getCode();
        this.MESSAGE = code.getMsg();
    }

    public void setResponse(SberOnlineCode code) {
        this.CODE = code.getCode();
        this.MESSAGE = code.getMsg();
    }
}
