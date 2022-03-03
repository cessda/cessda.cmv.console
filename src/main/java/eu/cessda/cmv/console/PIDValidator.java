/*
 * Copyright © 2017-2021 CESSDA ERIC (support@cessda.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.cessda.cmv.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

public class PIDValidator {
    private static final Logger log = LoggerFactory.getLogger(PIDValidator.class);

    private static final Set<String> ALLOWED_AGENCY_VALUES = Set.of("ARK", "DOI", "Handle", "URN");
    private static final EnumSet<PID.State> PID_STATES = EnumSet.allOf(PID.State.class);

    private PIDValidator() {
    }

    static PIDValidationResult validatePids(InputStream inputStream, XPathContext xPathContext) throws XPathExpressionException {
        var xpath = XPathFactory.newDefaultInstance().newXPath();
        xpath.setNamespaceContext(xPathContext.getNamespaceContext());

        var iDNoElement = (NodeList) xpath.compile(xPathContext.getXPath())
            .evaluate(new InputSource(inputStream), XPathConstants.NODESET);


        boolean validPids = false;
        var pidList = new ArrayList<PID>(iDNoElement.getLength());

        for (int i = 0; i < iDNoElement.getLength(); i++) {

            // Holds the state of the PID
            var state = EnumSet.noneOf(PID.State.class);

            // Validate URI and check if the URI is absolute.
            var agencyURIString = iDNoElement.item(i).getTextContent().trim();
            try {
                // Run the constructor
                var uri = new URI(agencyURIString);
                if (uri.isAbsolute()) {
                    // A valid absolute URI was found.
                    state.add(PID.State.VALID_URI);
                }
            } catch (URISyntaxException e) {
                log.debug("Invalid uri: {}", agencyURIString);
            }


            // Check if an agency attribute is present, and whether it has an allowed value.
            var agencyNode = iDNoElement.item(i).getAttributes().getNamedItem("agency");
            final String agencyTextContent;
            if (agencyNode != null) {
                state.add(PID.State.AGENCY_PRESENT);
                agencyTextContent = agencyNode.getTextContent();
                if (ALLOWED_AGENCY_VALUES.contains(agencyTextContent)) {
                    state.add(PID.State.AGENCY_ALLOWED_VALUE);
                } else {
                    log.debug("Invalid agency: {}", agencyTextContent);
                }
            } else {
                agencyTextContent = null;
            }

            // If all states are present, then the PID is valid
            if (state.containsAll(PID_STATES)) {
                validPids = true;
            }

            pidList.add(new PID(agencyTextContent, agencyURIString, state));
        }

        return new PIDValidationResult(validPids, pidList);
    }
}