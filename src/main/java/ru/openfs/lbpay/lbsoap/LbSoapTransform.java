package ru.openfs.lbpay.lbsoap;

import javax.inject.Singleton;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.dataformat.soap.SoapJaxbDataFormat;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;

import ru.openfs.lbpay.sberonline.model.SberOnlineMessage;

@Singleton
public class LbSoapTransform extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // define SOAP format
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat("api3",
                new ServiceInterfaceStrategy(api3.Api3PortType.class, true));

        // define format for SberOnline
        JaxbDataFormat sberOnline = new JaxbDataFormat(SberOnlineMessage.class.getPackage().getName());

        // marshal sberonline message
        from("direct:marshalSberOnline").id("MarshalSberOnline").marshal(sberOnline);

        // marshal object to soap
        from("direct:marshalSoap").id("MarshalSoapMessage").marshal(soap);

        // unmarshal byte[] to object
        from("direct:unmarshalSoap").id("UnmarshalSoapMessage").unmarshal(soap);

        // process fault
        from("direct:getFaultMessage").id("GetFaultMessage").transform(xpath("//detail/text()", String.class));
    }

}
