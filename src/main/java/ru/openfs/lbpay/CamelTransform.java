/*
 * Copyright [2021] [OpenFS.RU]
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
package ru.openfs.lbpay;

import javax.inject.Singleton;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.dataformat.soap.SoapDataFormat;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;

import ru.openfs.lbpay.model.SberOnlineMessage;

@Singleton
public class CamelTransform extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // define SOAP format
        SoapDataFormat soap = new SoapDataFormat("api3",
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
