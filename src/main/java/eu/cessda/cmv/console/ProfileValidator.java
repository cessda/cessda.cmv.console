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

import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.ValidationService;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.gesis.commons.resource.Resource;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileValidator {
    private final ValidationService.V10 validationService = new CessdaMetadataValidatorFactory().newValidationService();
    private final ConcurrentHashMap<URI, Resource> profileMap = new ConcurrentHashMap<>();

    /**
     * Validate the given XML document against a DDI profile.
     *
     * @param document       the document to validate
     * @param profileURI     the profile to validate the document against
     * @param validationGate the validation gate to use
     * @return the validation report
     */
    public ValidationReportV0 validateAgainstProfile(Resource document, URI profile, ValidationGateName validationGate) {
        // Load and cache the DDI profile
        var profileResource = profileMap.computeIfAbsent(profile, u -> {
            try {
                return Resource.newResource(u.toURL().openStream());
            } catch (IOException e) {
                throw new ProfileLoadFailedException(e);
            }
        });

        return validationService.validate(document, profileResource, validationGate);
    }
}
