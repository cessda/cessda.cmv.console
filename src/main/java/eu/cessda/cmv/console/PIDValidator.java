/*
 * Copyright © 2017-2024 CESSDA ERIC (support@cessda.eu)
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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("java:S5164")
class PIDValidator {
    private static final Logger log = LoggerFactory.getLogger(PIDValidator.class);

    private static final Set<String> ALLOWED_AGENCY_VALUES = Set.of("ark", "doi", "handle", "urn");
    private static final EnumSet<PID.State> PID_STATES = EnumSet.allOf(PID.State.class);

    private final ThreadLocal<XPath> xpathThreadLocal = ThreadLocal.withInitial(() -> XPathFactory.newDefaultInstance().newXPath());

    PIDValidationResult validatePIDs(Document document, DDIVersion xPathContext) throws XPathExpressionException {

        // Set namespace context
        var xpath = xpathThreadLocal.get();
        xpath.setNamespaceContext(xPathContext.getNamespaceContext());

        var iDNoElement = (NodeList) xpath.compile(xPathContext.getXPath()).evaluate(document, XPathConstants.NODESET);


        boolean validPids = false;
        var validPIDList = new ArrayList<PID>(iDNoElement.getLength());
        var invalidPIDList = new ArrayList<PID>(iDNoElement.getLength());

        for (int i = 0; i < iDNoElement.getLength(); i++) {

            // Holds the state of the PID
            var state = EnumSet.noneOf(PID.State.class);

            // Extract persistent identifiers from the element
            var pidElement = xPathContext.getPID(iDNoElement.item(i));

            // Validate URI and check if the URI is absolute.
            try {
                // Run the constructor
                var uri = new URI(pidElement.uri());

                // Check if the URI is a valid DOI
                if ("doi".equals(uri.getScheme())
                    // Detect if the given URI is a DOI, stripping out the first / in the path if present
                    || uri.getPath() != null && PIDValidator.doiStringValidator(uri.getPath().replaceFirst("^/", ""))
                    // Detect if the URI is an HTTP(S) URL
                    // TODO - replace with more specific PID validation
                    || ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()) || "urn".equals(uri.getScheme()))) {
                    state.add(PID.State.VALID_URI);
                }
            } catch (URISyntaxException e) {
                log.debug("Invalid uri: {}", pidElement.uri());
            }


            // Check if an agency attribute is present, and whether it has an allowed value.
            if (pidElement.agency() != null) {
                state.add(PID.State.AGENCY_PRESENT);
                if (ALLOWED_AGENCY_VALUES.contains(pidElement.agency().toLowerCase(Locale.ROOT))) {
                    state.add(PID.State.AGENCY_ALLOWED_VALUE);
                } else {
                    log.debug("Invalid agency: {}", pidElement.agency());
                }
            }

            // Collect all PID information
            var pid = new PID(pidElement.agency(), pidElement.uri(), state);

            // If all states are present, then the PID is valid
            if (state.containsAll(PID_STATES)) {
                validPIDList.add(pid);
                validPids = true;
            } else {
                invalidPIDList.add(pid);
            }
        }

        return new PIDValidationResult(validPids, validPIDList, invalidPIDList);
    }

    /**
     * Validate if the given input string is a valid DOI.
     *
     * @param input the DOI to validate
     * @return {@code true} if the input is a valid DOI, {@code false} otherwise
     */
    static boolean doiStringValidator(String input) {
        // Split using /
        var splitInput = input.split("/", 2);

        // Check if the DOI is made of two components, prefix and suffix
        if (splitInput.length != 2) {
            return false;
        }

        // Split the prefix into sections
        var prefix = splitInput[0];
        var splitPrefix = prefix.split("\\.");

        // Directory indicator must be 10
        if (!"10".equals(splitPrefix[0])) {
            return false;
        }

        // Registrant codes and sub-elements should be integers
        try {
            for (int i = 1; i < splitPrefix.length; i++) {
                Integer.parseInt(splitPrefix[1]);
            }
        } catch (NumberFormatException e) {
            return false;
        }

        // Valid DOI
        return true;
    }
}
