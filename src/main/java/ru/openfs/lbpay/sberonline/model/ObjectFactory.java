package ru.openfs.lbpay.sberonline.model;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

@XmlRegistry
public class ObjectFactory {
    
    private final QName responseQNAME = new QName("", "response");

    public ObjectFactory() {}

    public SberOnlineMessage createSberOnlineMessageType() {
        return new SberOnlineMessage();
    }

    @XmlElementDecl(namespace = "", name = "response")
    public JAXBElement<SberOnlineMessage> createResponse(SberOnlineMessage value) {
        return new JAXBElement<SberOnlineMessage>(responseQNAME, SberOnlineMessage.class, null, value);
    }

}
