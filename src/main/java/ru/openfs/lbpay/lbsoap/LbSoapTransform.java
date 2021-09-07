package ru.openfs.lbpay.lbsoap;

import javax.inject.Singleton;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.soap.SoapJaxbDataFormat;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;

@Singleton
public class LbSoapTransform extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        SoapJaxbDataFormat soap = new SoapJaxbDataFormat(
                "api3",
                new ServiceInterfaceStrategy(api3.Api3PortType.class, true));

        // marshal object to soap
        from("direct:marshalSoap").id("MarshalSoapMessage").marshal(soap);

        // unmarshal byte[] to object
        from("direct:unmarshalSoap").id("UnmarshalSoapMessage").unmarshal(soap);

        // process fault
        from("direct:getFaultMessage").id("GetFaultMessage").transform(xpath("//detail/text()", String.class));
    }

}
