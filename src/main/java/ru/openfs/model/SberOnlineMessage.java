package ru.openfs.model;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "RESPONSE")
public class SberOnlineMessage {
    public int CODE;
    public String MESSAGE;
}
