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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cessda.cmv.core.NotDocumentException;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.mediatype.validationreport.ValidationReport;
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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    private final Configuration configuration;

    ValidatorTest() {
        configuration = new Configuration(Path.of("input"), null);
    }

    @Test
    void shouldValidateDocuments() throws IOException, SAXException, URISyntaxException, NotDocumentException {
        var validator = new Validator(configuration, new ObjectMapper());

        var resultsMap = new HashMap<Path, ValidationResults>();
        var ddi25Documents = Path.of(Objects.requireNonNull(this.getClass().getResource("/ddi_2_5")).toURI());
        try (var directoryStream = Files.newDirectoryStream(ddi25Documents, Validator::xmlPathFilter)) {
            for (var document : directoryStream) {
                var validationResultsEntry = validator.validateDocuments(
                    document,
                    DDIVersion.DDI_2_5,
                    Objects.requireNonNull(this.getClass().getResource("/profiles/cdc-ddi2.5.xml")).toURI(),
                    ValidationGateName.BASIC
                );
                resultsMap.put(document, validationResultsEntry);
            }
        }

        // Assert that the correct amount of documents were validated.
        assertEquals(4, resultsMap.size());

        // Assert that there is one result with either constraint or schema validation errors.
        assertThat(resultsMap.values()).map(ValidationResults::schemaViolations).isNotEmpty();
        assertThat(resultsMap.values()).map(ValidationResults::report).map(ValidationReport::getConstraintViolations).isNotEmpty();
    }

    @Test
    void shouldSkipCMVWhenProfileIsNotProvided() throws IOException, SAXException, URISyntaxException, NotDocumentException {
        var validator = new Validator(configuration, new ObjectMapper());

        var resultsMap = new HashMap<Path, ValidationResults>();
        var ddi25Documents = Path.of(Objects.requireNonNull(this.getClass().getResource("/ddi_2_5")).toURI());
        try (var directoryStream = Files.newDirectoryStream(ddi25Documents, Validator::xmlPathFilter)) {
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
        assertEquals(4, resultsMap.size());

        // Assert that only schema violations are present
        assertThat(resultsMap.values()).map(ValidationResults::schemaViolations).anyMatch(schemaViolations -> !schemaViolations.isEmpty());
        assertThat(resultsMap.values()).map(ValidationResults::report).map(ValidationReport::getConstraintViolations).allMatch(List::isEmpty);
    }

    @Test
    void shouldThrowOnInvalidXML() throws URISyntaxException, SAXException {
        var validator = new Validator(configuration, new ObjectMapper());

        var invalidDocument = Path.of(Objects.requireNonNull(this.getClass().getResource("/malformed.xml")).toURI());
        assertThrows(SAXException.class, () -> validator.validateDocuments(
            invalidDocument,
            DDIVersion.DDI_2_5,
            Objects.requireNonNull(this.getClass().getResource("/profiles/cdc-ddi2.5.xml")).toURI(),
            ValidationGateName.BASIC)
        );
    }

    @Test
    void shouldCopyValidatedRecords(@TempDir Path source, @TempDir Path destination) throws URISyntaxException, IOException {
        // Discover test documents and copy them
        var ddi25Documents = Path.of(Objects.requireNonNull(this.getClass().getResource("/ddi_2_5")).toURI());
        var ddi25Source = source.resolve("ddi_2_5");

        // Create the subdirectory
        Files.createDirectory(ddi25Source);

        try (var fileList = Files.newDirectoryStream(ddi25Documents)) {
            for (var document : fileList) {
                Files.copy(document, ddi25Source.resolve(document.getFileName()));
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        // Write repository configuration
        var repositoryConfiguration = new Repository(
            "test",
            URI.create("http://test/oai"),
            DDIVersion.DDI_2_5,
            Objects.requireNonNull(this.getClass().getResource("/profiles/cdc-ddi2.5.xml")).toURI(),
            ValidationGateName.BASIC
        );
        new ObjectMapper().writeValue(ddi25Source.resolve("pipeline.json").toFile(), repositoryConfiguration);

        assertDoesNotThrow(() -> Validator.main(new String[]{
            source.toString(),
            // Set the destination
            "--destination", destination.toString()
        }));

        // Assert that files were copied
        assertThat(destination.resolve("ddi_2_5"))
            // valid.xml and pid.xml are valid
            .isDirectoryContaining("glob:**/valid.xml")
            .isDirectoryContaining("glob:**/pid.xml")
            // invalid.xml is invalid, and shouldn't be copied
            .isDirectoryNotContaining("glob:**/invalid.xml");
    }

    @Test
    void shouldHandleMissingDirectory() throws SAXException {
        var objectMapper = new ObjectMapper();
        var validator = new Validator(configuration, objectMapper);
        var directoryWalker = new Validator.DirectoryWalker(objectMapper, validator, "");

        assertThatNoException().isThrownBy(() -> directoryWalker.walkDirectory(Path.of("directory/does/not/exist")));
    }
}
