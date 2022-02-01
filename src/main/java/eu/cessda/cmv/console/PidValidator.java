package eu.cessda.cmv.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public class PidValidator {
    private static final Logger log = LoggerFactory.getLogger(PidValidator.class);

    private static final String XPATH = "//ddi:codeBook//ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo";
    private static final Set<String> agencyMap = Set.of("ARK", "DOI", "Handle", "URN");

    private static final NamespaceContext DDI_2_5_NS_CONTEXT = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            if ("ddi".equals(prefix)) {
                return "ddi:codebook:2_5";
            }
            return null;
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return null;
        }
    };

    private PidValidator() {
    }

    static PidValidationResult validatePids(InputStream inputStream) throws XPathExpressionException {
        var xpath = XPathFactory.newDefaultInstance().newXPath();
        xpath.setNamespaceContext(DDI_2_5_NS_CONTEXT);

        var iDNoElement = (NodeList) xpath.compile(XPATH)
            .evaluate(new InputSource(inputStream), XPathConstants.NODESET);

        boolean isAgencyPresent = false;
        boolean isAgencyAllowedValue = false;
        boolean isAgencyValidURI = false;

        for (int i = 0; i < iDNoElement.getLength(); i++) {
            var agencyNode = iDNoElement.item(i).getAttributes().getNamedItem("agency");

            if (agencyNode != null) {
                isAgencyPresent = true;
                if (!agencyMap.contains(agencyNode.getTextContent())) {
                    log.debug("Invalid agency: {}", agencyNode.getTextContent());
                } else {
                    isAgencyAllowedValue = true;
                    var elementText = iDNoElement.item(i).getTextContent();
                    try {
                        // Run the constructor
                        new URI(elementText);
                        isAgencyValidURI = true;
                    } catch (URISyntaxException e) {
                        log.debug("Invalid uri: {}", elementText);
                    }
                }
            }
        }

        final List<Pid> invalidPids;

        if (!isAgencyValidURI) {
            invalidPids = new ArrayList<>(iDNoElement.getLength());
            for (int i = 0; i < iDNoElement.getLength(); i++) {

                var agency = ofNullable(iDNoElement.item(i).getAttributes().getNamedItem("agency"))
                    .map(Node::getTextContent).orElse(null);

                var uri = iDNoElement.item(i).getTextContent();

                invalidPids.add(new Pid(agency, uri));
            }
        } else {
            invalidPids = emptyList();
        }

        return new PidValidationResult(isAgencyPresent, isAgencyAllowedValue, isAgencyValidURI, invalidPids);
    }

    public record Pid(String agency, String uri) {
    }

    public record PidValidationResult(boolean isAgencyPresent, boolean isAgencyAllowedValue, boolean isAgencyValidURI,
                                      List<Pid> invalidPids) {
    }
}
