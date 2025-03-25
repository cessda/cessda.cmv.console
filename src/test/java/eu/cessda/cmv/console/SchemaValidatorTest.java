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

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {
    @Test
    void shouldValidateDocument() throws IOException, SAXException {
        // Load a valid DDI document.
        var validDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/valid.xml");

        var result = new SchemaValidator().getSchemaViolations(validDDIDocument);

        // There should be no schema violations.
        assertNotNull(result.document());
        assertNull(Validator.extractURL(result.document()));
        assertTrue(result.schemaViolations().isEmpty());
    }

    @Test
    void shouldValidateOAIDocument() throws IOException, SAXException {
        // Load a valid DDI document.
        var validDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/valid_oai.xml");

        var result = new SchemaValidator().getSchemaViolations(validDDIDocument);

        // There should be no schema violations.
        assertNotNull(result.document());
        assertEquals(URI.create("https://oai.ukdataservice.ac.uk:8443/oai/provider"), Validator.extractURL(result.document()));
        assertTrue(result.schemaViolations().isEmpty());
    }

    @Test
    void shouldReportValidationFailuresInInvalidDocument() throws IOException, SAXException {
        // Load an invalid DDI document.
        var invalidDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/invalid.xml");

        var result = new SchemaValidator().getSchemaViolations(invalidDDIDocument);

        // There should be 5 schema violations.
        assertNotNull(result.document());
        assertEquals(5, result.schemaViolations().size());
    }

    @Test
    void shouldValidateNesstarDocument() throws IOException, SAXException {
        // Load a NESSTAR DDI document.
        var nesstarDocument = this.getClass().getResourceAsStream("/nesstar/NSD1367.xml");

        var result = new SchemaValidator().getSchemaViolations(nesstarDocument);

        // There should be 2 schema violations
        assertNotNull(result.document());
        assertEquals(2, result.schemaViolations().size());
    }

    @Test
    void shouldThrowOnFatalError() throws SAXException {
        var emptyStream = InputStream.nullInputStream();

        // Attempting to parse an empty stream should throw an exception.
        var schemaValidator = new SchemaValidator();
        assertThrows(SAXException.class, () -> schemaValidator.getSchemaViolations(emptyStream));
    }
}
