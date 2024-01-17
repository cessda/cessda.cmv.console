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

import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.NotDocumentException;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.ValidationService;
import eu.cessda.cmv.core.mediatype.validationreport.ValidationReport;
import org.gesis.commons.resource.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileValidator {
    private final ValidationService validationService = new CessdaMetadataValidatorFactory().newValidationService();
    /**
     * Cache for CMV profiles
     */
    private final ConcurrentHashMap<URI, Resource> profileMap = new ConcurrentHashMap<>();

    /**
     * Validate the given XML document against a DDI profile.
     *
     * @param document       the document to validate
     * @param profile        the profile to validate the document against
     * @param validationGate the validation gate to use
     * @return the validation report
     */
    public ValidationReport validateAgainstProfile(Resource document, URI profile, ValidationGateName validationGate) throws IOException, NotDocumentException {
        // Load and cache the DDI profile
        var profileResource = profileMap.computeIfAbsent(profile, CachedProfileResource::new);
        return validationService.validate(document, profileResource, validationGate);
    }

    private static class CachedProfileResource implements Resource {
        private final URI source;
        private final byte[] buffer;

        /**
         * Create a new instance of a CachedProfileResource, reading the URL into a buffer.
         *
         * @param source the URI of the profile.
         * @throws ProfileLoadFailedException if an IO error occurred.
         */
        private CachedProfileResource(URI source) {
            this.source = source;
            try (var inputStream = source.toURL().openStream()) {
                this.buffer = inputStream.readAllBytes();
            } catch (IOException e) {
                throw new ProfileLoadFailedException(e);
            }
        }

        @Override
        public URI getUri() {
            return source;
        }

        @Override
        public InputStream readInputStream() {
            return new ByteArrayInputStream(buffer);
        }
    }
}
