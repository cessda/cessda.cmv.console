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

import eu.cessda.cmv.core.ValidationGateName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    private final Configuration configuration;

    ValidatorTest() {
        configuration = new Configuration(Path.of("input"), null, null);
    }

    @Test
    void shouldValidateDocuments() throws IOException, SAXException, URISyntaxException {
        var validator = new Validator(configuration);

        var resultsMap = new HashMap<Path, ValidationResults>();
        var ddi25Documents = Path.of(this.getClass().getResource("/ddi_2_5").toURI());
        try (var directoryStream = Files.newDirectoryStream(ddi25Documents)) {
            for (var document : directoryStream) {
                var validationResultsEntry = validator.validateDocuments(
                    document,
                    DDIVersion.DDI_2_5,
                    URI.create("https://cmv.cessda.eu/profiles/cdc/ddi-2.5/latest/profile.xml"),
                    ValidationGateName.BASIC
                );
                resultsMap.put(validationResultsEntry.getKey(), validationResultsEntry.getValue());
            }
        }

        // Assert that the correct amount of documents were validated.
        assertEquals(3, resultsMap.size());

        // Assert that there is one result with either constraint or schema validation errors.
        assertTrue(resultsMap.entrySet().stream().anyMatch(r -> !r.getValue().schemaViolations().isEmpty()));
        assertTrue(resultsMap.entrySet().stream().anyMatch(r -> !r.getValue().report().getConstraintViolations().isEmpty()));
    }

    @Test
    void shouldThrowOnInvalidXML() throws URISyntaxException {
        var validator = new Validator(configuration);

        var invalidDocument = Path.of(this.getClass().getResource("/malformed.xml").toURI());
        assertThrows(SAXException.class, () -> validator.validateDocuments(
            invalidDocument,
            DDIVersion.DDI_2_5,
            URI.create("https://cmv.cessda.eu/profiles/cdc/ddi-2.5/latest/profile.xml"),
            ValidationGateName.BASIC)
        );
    }
}
