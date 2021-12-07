package eu.cessda.cmv.console;

import eu.cessda.cmv.core.ValidationGateName;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    private final Configuration configuration;

    ValidatorTest() throws IOException {
        configuration = Validator.parseConfiguration();
    }

    @Test
    void shouldValidateDocuments() throws IOException, SAXException, URISyntaxException {
        var validator = new Validator(configuration);

        var resultsMap = new HashMap<Path, ValidationResults>();
        var ddi25Documents = Path.of(this.getClass().getResource("/ddi_2_5").toURI());
        try (var directoryStream = Files.newDirectoryStream(ddi25Documents)) {
            for (var document : directoryStream) {
                var validationResultsEntry = validator.validateDocuments(document, configuration.profiles().get("DDI_2_5"), ValidationGateName.BASIC);
                resultsMap.put(validationResultsEntry.getKey(), validationResultsEntry.getValue());
            }
        }

        // Assert that the correct amount of documents were validated.
        assertEquals(2, resultsMap.size());

        // Assert that there is one result with either constraint or schema validation errors.
        assertTrue(resultsMap.entrySet().stream().anyMatch(r -> !r.getValue().schemaViolations().isEmpty()));
        assertTrue(resultsMap.entrySet().stream().anyMatch(r -> !r.getValue().report().getConstraintViolations().isEmpty()));
    }

    @Test
    void shouldThrowOnInvalidXML() throws URISyntaxException {
        var validator = new Validator(configuration);

        var invalidDocument = Path.of(this.getClass().getResource("/malformed.xml").toURI());
        assertThrows(SAXException.class, () -> validator.validateDocuments(invalidDocument, configuration.profiles().get("DDI_2_5"), ValidationGateName.BASIC));
    }
}
