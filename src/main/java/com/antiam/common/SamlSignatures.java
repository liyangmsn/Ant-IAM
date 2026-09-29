package com.antiam.common;

import java.io.StringReader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 基于 JDK XML Digital Signature 为 SAML 断言生成 enveloped 签名（exc-c14n + RSA-SHA256），
 * 签名元素按 SAML 2.0 Schema 要求插入在 Assertion 的 Issuer 之后。
 */
public final class SamlSignatures {

    private static final String SAML_ASSERTION_NS = "urn:oasis:names:tc:SAML:2.0:assertion";

    private SamlSignatures() {
    }

    public static String signAssertion(String responseXml, String assertionId, PrivateKey privateKey, X509Certificate certificate) {
        try {
            Document document = parse(responseXml);
            Element assertion = findAssertion(document, assertionId);
            Element response = document.getDocumentElement();
            if (response.hasAttribute("ID")) {
                response.setIdAttribute("ID", true);
            }
            assertion.setIdAttribute("ID", true);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            Reference reference = factory.newReference(
                "#" + assertionId,
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(
                    factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                    factory.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null)),
                null,
                null);
            SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                List.of(reference));
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(List.of(keyInfoFactory.newX509Data(List.of(certificate))));

            DOMSignContext context = new DOMSignContext(privateKey, assertion, issuerSibling(assertion));
            context.setDefaultNamespacePrefix("ds");
            factory.newXMLSignature(signedInfo, keyInfo).sign(context);
            return serialize(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign SAML assertion", ex);
        }
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Element findAssertion(Document document, String assertionId) {
        NodeList assertions = document.getElementsByTagNameNS(SAML_ASSERTION_NS, "Assertion");
        for (int index = 0; index < assertions.getLength(); index++) {
            Element element = (Element) assertions.item(index);
            if (assertionId.equals(element.getAttribute("ID"))) {
                return element;
            }
        }
        throw new IllegalStateException("SAML assertion not found: " + assertionId);
    }

    // 返回 Issuer 之后的第一个元素节点，签名插入到它之前。
    private static Node issuerSibling(Element assertion) {
        for (Node child = assertion.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && SAML_ASSERTION_NS.equals(element.getNamespaceURI())
                && "Issuer".equals(element.getLocalName())) {
                Node next = element.getNextSibling();
                while (next != null && next.getNodeType() != Node.ELEMENT_NODE) {
                    next = next.getNextSibling();
                }
                return next;
            }
        }
        return assertion.getFirstChild();
    }

    private static String serialize(Document document) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
