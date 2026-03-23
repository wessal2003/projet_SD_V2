package com.smartcity.trafficlight;

import com.smartcity.common.config.AppConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class TrafficLightJaxRpcServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficLightJaxRpcServerMain.class);
    private static final String TARGET_NAMESPACE = "http://trafficlight.jaxrpc.smartcity.com/";
    private static final String SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SERVICE_PATH = "/axis/services/ServiceFeuxSignalisation";
    private static final String SERVICE_WSDL_PATH = "/axis/services/ServiceFeuxSignalisation?wsdl";
    private static final String WSDL_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <wsdl:definitions
                xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/"
                xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                xmlns:tns="%s"
                targetNamespace="%s">
              <wsdl:types>
                <xsd:schema targetNamespace="%s">
                  <xsd:element name="extendGreen">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="intersectionId" type="xsd:string"/>
                        <xsd:element name="additionalSeconds" type="xsd:int"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                  <xsd:element name="extendGreenResponse">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="return" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                  <xsd:element name="blockRoad">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="intersectionId" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                  <xsd:element name="blockRoadResponse">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="return" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                  <xsd:element name="getGreenDurations"/>
                  <xsd:element name="getGreenDurationsResponse">
                    <xsd:complexType>
                      <xsd:sequence>
                        <xsd:element name="return" type="xsd:string"/>
                      </xsd:sequence>
                    </xsd:complexType>
                  </xsd:element>
                </xsd:schema>
              </wsdl:types>

              <wsdl:message name="extendGreenRequest">
                <wsdl:part name="parameters" element="tns:extendGreen"/>
              </wsdl:message>
              <wsdl:message name="extendGreenResponse">
                <wsdl:part name="parameters" element="tns:extendGreenResponse"/>
              </wsdl:message>
              <wsdl:message name="blockRoadRequest">
                <wsdl:part name="parameters" element="tns:blockRoad"/>
              </wsdl:message>
              <wsdl:message name="blockRoadResponse">
                <wsdl:part name="parameters" element="tns:blockRoadResponse"/>
              </wsdl:message>
              <wsdl:message name="getGreenDurationsRequest">
                <wsdl:part name="parameters" element="tns:getGreenDurations"/>
              </wsdl:message>
              <wsdl:message name="getGreenDurationsResponse">
                <wsdl:part name="parameters" element="tns:getGreenDurationsResponse"/>
              </wsdl:message>

              <wsdl:portType name="ServiceFeuxSignalisationPortType">
                <wsdl:operation name="extendGreen">
                  <wsdl:input message="tns:extendGreenRequest"/>
                  <wsdl:output message="tns:extendGreenResponse"/>
                </wsdl:operation>
                <wsdl:operation name="blockRoad">
                  <wsdl:input message="tns:blockRoadRequest"/>
                  <wsdl:output message="tns:blockRoadResponse"/>
                </wsdl:operation>
                <wsdl:operation name="getGreenDurations">
                  <wsdl:input message="tns:getGreenDurationsRequest"/>
                  <wsdl:output message="tns:getGreenDurationsResponse"/>
                </wsdl:operation>
              </wsdl:portType>

              <wsdl:binding name="ServiceFeuxSignalisationBinding" type="tns:ServiceFeuxSignalisationPortType">
                <soap:binding style="rpc" transport="http://schemas.xmlsoap.org/soap/http"/>
                <wsdl:operation name="extendGreen">
                  <soap:operation soapAction=""/>
                  <wsdl:input><soap:body use="literal" namespace="%s"/></wsdl:input>
                  <wsdl:output><soap:body use="literal" namespace="%s"/></wsdl:output>
                </wsdl:operation>
                <wsdl:operation name="blockRoad">
                  <soap:operation soapAction=""/>
                  <wsdl:input><soap:body use="literal" namespace="%s"/></wsdl:input>
                  <wsdl:output><soap:body use="literal" namespace="%s"/></wsdl:output>
                </wsdl:operation>
                <wsdl:operation name="getGreenDurations">
                  <soap:operation soapAction=""/>
                  <wsdl:input><soap:body use="literal" namespace="%s"/></wsdl:input>
                  <wsdl:output><soap:body use="literal" namespace="%s"/></wsdl:output>
                </wsdl:operation>
              </wsdl:binding>

              <wsdl:service name="ServiceFeuxSignalisationService">
                <wsdl:port name="ServiceFeuxSignalisationPort" binding="tns:ServiceFeuxSignalisationBinding">
                  <soap:address location="%s"/>
                </wsdl:port>
              </wsdl:service>
            </wsdl:definitions>
            """;

    public static void main(String[] args) throws Exception {
        int port = 8084;
        ServiceFeuxSignalisation service = new ServiceFeuxSignalisation();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(SERVICE_PATH, new SoapHandler(service));
        server.createContext("/", new RootHandler());
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
        LOG.info("ServiceFeuxSignalisation JAX-RPC started at {}", AppConfig.trafficLightJaxRpcEndpoint());
        Thread.currentThread().join();
    }

    private static final class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = "SmartTraffic JAX-RPC Traffic Light Service";
            send(exchange, 200, "text/plain; charset=utf-8", body);
        }
    }

    private static final class SoapHandler implements HttpHandler {
        private final ServiceFeuxSignalisation service;

        private SoapHandler(ServiceFeuxSignalisation service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleGet(exchange);
                    return;
                }

                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                    return;
                }

                byte[] requestBytes = exchange.getRequestBody().readAllBytes();
                String response = invokeService(requestBytes);
                send(exchange, 200, "text/xml; charset=utf-8", response);
            } catch (Exception ex) {
                LOG.warn("JAX-RPC SOAP handler failed: {}", ex.getMessage());
                send(exchange, 500, "text/xml; charset=utf-8", soapFault(ex.getMessage()));
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.equalsIgnoreCase("wsdl")) {
                String wsdl = WSDL_TEMPLATE.formatted(
                        TARGET_NAMESPACE, TARGET_NAMESPACE, TARGET_NAMESPACE,
                        TARGET_NAMESPACE, TARGET_NAMESPACE,
                        TARGET_NAMESPACE, TARGET_NAMESPACE,
                        TARGET_NAMESPACE, TARGET_NAMESPACE,
                        "http://localhost:8084" + SERVICE_PATH);
                send(exchange, 200, "text/xml; charset=utf-8", wsdl);
                return;
            }
            send(exchange, 200, "text/plain; charset=utf-8", "ServiceFeuxSignalisation JAX-RPC");
        }

        private String invokeService(byte[] requestBytes) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(requestBytes));
            Element body = firstElement(document.getDocumentElement(), SOAP_ENV, "Body");
            if (body == null) {
                throw new IllegalStateException("SOAP Body not found");
            }
            Element operation = firstChildElement(body);
            if (operation == null) {
                throw new IllegalStateException("SOAP operation not found");
            }

            String operationName = operation.getLocalName();
            String result = switch (operationName) {
                case "extendGreen" -> service.extendGreen(
                        childText(document, operation, "intersectionId"),
                        Integer.parseInt(childText(document, operation, "additionalSeconds")));
                case "blockRoad" -> service.blockRoad(childText(document, operation, "intersectionId"));
                case "getGreenDurations" -> service.getGreenDurations();
                default -> throw new IllegalArgumentException("Unsupported operation: " + operationName);
            };
            return soapResponse(operationName + "Response", result);
        }

        private Element firstElement(Element parent, String namespace, String localName) {
            NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
            if (nodes.getLength() == 0) {
                return null;
            }
            return (Element) nodes.item(0);
        }

        private Element firstChildElement(Element parent) {
            Node node = parent.getFirstChild();
            while (node != null) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    return (Element) node;
                }
                node = node.getNextSibling();
            }
            return null;
        }

        private String childText(Document document, Element parent, String childLocalName) {
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && childLocalName.equals(node.getLocalName())) {
                    Element element = (Element) node;
                    String text = element.getTextContent();
                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }

                    String href = element.getAttribute("href");
                    if (href != null && !href.isBlank()) {
                        String refId = href.startsWith("#") ? href.substring(1) : href;
                        Element multiRef = findElementById(document.getDocumentElement(), refId);
                        if (multiRef != null) {
                            String multiRefText = multiRef.getTextContent();
                            if (multiRefText != null && !multiRefText.isBlank()) {
                                return multiRefText.trim();
                            }
                        }
                    }
                }
            }
            throw new IllegalArgumentException("Missing SOAP parameter: " + childLocalName);
        }

        private Element findElementById(Element root, String id) {
            if (id == null || id.isBlank()) {
                return null;
            }

            if (id.equals(root.getAttribute("id"))) {
                return root;
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element found = findElementById((Element) node, id);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }

    private static String soapResponse(String responseName, String value) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="%s">
                  <soapenv:Body>
                    <tns:%s>
                      <return>%s</return>
                    </tns:%s>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(TARGET_NAMESPACE, responseName, escapeXml(value), responseName);
    }

    private static String soapFault(String message) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <soapenv:Fault>
                      <faultcode>soapenv:Server</faultcode>
                      <faultstring>%s</faultstring>
                    </soapenv:Fault>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(escapeXml(message == null ? "Unknown server error" : message));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
