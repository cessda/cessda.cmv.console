/*
 * Copyright © 2017-2025 CESSDA ERIC (support@cessda.eu)
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

import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.NotDocumentException;
import eu.cessda.cmv.core.Profile;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.mediatype.validationreport.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileValidator {
    private static final Logger log = LoggerFactory.getLogger(ProfileValidator.class);

    /**
     * Validator factory, creates the CMV documents and profiles
     */
    private final CessdaMetadataValidatorFactory validatorFactory = new CessdaMetadataValidatorFactory();

    /**
     * Cache for CMV profiles
     */
    private final ConcurrentHashMap<URI, Profile> profileMap = new ConcurrentHashMap<>();

    /**
     * Validate the given XML document against a DDI profile.
     *
     * @param documentStream the document to validate
     * @param profileURI     the profile to validate the document against
     * @param validationGate the validation gate to use
     * @return the validation report
     */
    public ValidationReport validateAgainstProfile(InputStream documentStream, URI profileURI, ValidationGateName validationGate) throws IOException, NotDocumentException {
        // Load and cache the DDI profile
        var profile = profileMap.computeIfAbsent(profileURI, p -> {
            try {
                log.debug("Parsing CMV profile \"{}\"", p);
                return validatorFactory.newProfile(p);
            } catch (NotDocumentException | IOException e) {
                throw new ProfileLoadFailedException(e);
            }
        });
        var document = validatorFactory.newDocument(documentStream);
        return validatorFactory.validate(document, profile, validationGate);
    }
}
