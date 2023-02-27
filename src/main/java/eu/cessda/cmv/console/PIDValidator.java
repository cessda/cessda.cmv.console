/*
 * Copyright © 2017-2023 CESSDA ERIC (support@cessda.eu)
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

    static PIDValidationResult validatePids(InputStream inputStream, DDIVersion xPathContext) throws XPathExpressionException {
        var xpath = XPathFactory.newDefaultInstance().newXPath();
        xpath.setNamespaceContext(xPathContext.getNamespaceContext());

        var iDNoElement = (NodeList) xpath.compile(xPathContext.getXPath())
            .evaluate(new InputSource(inputStream), XPathConstants.NODESET);


        boolean validPids = false;
        var pidList = new ArrayList<PID>(iDNoElement.getLength());

        for (int i = 0; i < iDNoElement.getLength(); i++) {

            // Holds the state of the PID
            var state = EnumSet.noneOf(PID.State.class);

            // Extract persistent identifiers from the element
            var pidElement = xPathContext.getPid(iDNoElement.item(i));

            // Validate URI and check if the URI is absolute.
            try {
                // Run the constructor
                var uri = new URI(pidElement.uri());

                // Check if the URI is a valid DOI
                if ("doi".equals(uri.getScheme())
                    // Detect if the given URI is a DOI, stripping out the first / in the path if present
                    || uri.getPath() != null && PIDValidator.doiStringValidator(uri.getPath().replaceFirst("^/", ""))
                    // Detect if the URI is an HTTP(S) URL that is absolute
                    // TODO - replace with more specific PID validation
                    || ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.isAbsolute()) {
                    state.add(PID.State.VALID_URI);
                }
            } catch (URISyntaxException e) {
                log.debug("Invalid uri: {}", pidElement.uri());
            }


            // Check if an agency attribute is present, and whether it has an allowed value.
            if (pidElement.agency() != null) {
                state.add(PID.State.AGENCY_PRESENT);
                if (ALLOWED_AGENCY_VALUES.contains(pidElement.agency())) {
                    state.add(PID.State.AGENCY_ALLOWED_VALUE);
                } else {
                    log.debug("Invalid agency: {}", pidElement.agency());
                }
            }

            // If all states are present, then the PID is valid
            if (state.containsAll(PID_STATES)) {
                validPids = true;
            }

            pidList.add(new PID(pidElement.agency(), pidElement.uri(), state));
        }

        return new PIDValidationResult(validPids, pidList);
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
