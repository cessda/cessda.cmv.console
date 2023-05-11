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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                resultsMap.put(document, validationResultsEntry);
            }
        }

        // Assert that the correct amount of documents were validated.
        assertEquals(3, resultsMap.size());

        // Assert that there is one result with either constraint or schema validation errors.
        assertThat(resultsMap.values()).map(ValidationResults::schemaViolations).anyMatch(schemaViolations -> !schemaViolations.isEmpty());
        assertThat(resultsMap.values()).map(ValidationResults::report).map(ValidationReportV0::getConstraintViolations).anyMatch(c -> !c.isEmpty());
    }

    @Test
    void shouldSkipCMVWhenProfileIsNotProvided() throws IOException, SAXException, URISyntaxException {
        var validator = new Validator(configuration);

        var resultsMap = new HashMap<Path, ValidationResults>();
        var ddi25Documents = Path.of(this.getClass().getResource("/ddi_2_5").toURI());
        try (var directoryStream = Files.newDirectoryStream(ddi25Documents)) {
            for (var document : directoryStream) {
                var validationResultsEntry = validator.validateDocuments(
                    document,
                    DDIVersion.DDI_2_5,
                    null,
                    null
                );
                resultsMap.put(document, validationResultsEntry);
            }
        }

        // Assert that the correct amount of documents were validated.
        assertEquals(3, resultsMap.size());

        // Assert that only schema violations are present
        assertThat(resultsMap.values()).map(ValidationResults::schemaViolations).anyMatch(schemaViolations -> !schemaViolations.isEmpty());
        assertThat(resultsMap.values()).map(ValidationResults::report).map(ValidationReportV0::getConstraintViolations).allMatch(List::isEmpty);
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

    @Test
    void shouldCopyValidatedRecords(@TempDir Path source, @TempDir Path destination) throws URISyntaxException, IOException {
        // Discover test documents and copy them
        var ddi25Documents = Path.of(this.getClass().getResource("/ddi_2_5").toURI());
        try (var fileList = Files.newDirectoryStream(ddi25Documents)) {
            for (var document : fileList) {
                Files.copy(document, source.resolve(document.getFileName()));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        // Write repository configuration
        var repositoryConfiguration = new Repository(
            "test",
            DDIVersion.DDI_2_5,
            this.getClass().getResource("/profiles/cdc-ddi2.5.xml").toURI(),
            ValidationGateName.BASIC
        );
        new ObjectMapper().writeValue(source.resolve("pipeline.json").toFile(), repositoryConfiguration);

        assertDoesNotThrow(() -> Validator.main(new String[]{
            source.toString(),
            // Set the destination
            "--destination", destination.toString()
        }));

        // Assert that files were copied
        assertThat(destination)
            // valid.xml and pid.xml are valid
            .isDirectoryContaining("glob:**/valid.xml")
            .isDirectoryContaining("glob:**/pid.xml")
            // invalid.xml is invalid, and shouldn't be copied
            .isDirectoryNotContaining("glob:**/invalid.xml");
    }
}
