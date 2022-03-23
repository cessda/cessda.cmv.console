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

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class SchemaValidatorTest {
    @Test
    void shouldValidateDocument() throws IOException, SAXException {
        // Load a valid DDI document.
        var validDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/valid.xml");

        var schemaViolations = DDIVersion.DDI_2_5.getSchemaValidator().getSchemaViolations(validDDIDocument);

        // There should be no schema violations.
        assertTrue(schemaViolations.isEmpty());
    }

    @Test
    void shouldReportValidationFailuresInInvalidDocument() throws IOException, SAXException {
        // Load an invalid DDI document.
        var invalidDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/invalid.xml");

        var schemaViolations = DDIVersion.DDI_2_5.getSchemaValidator().getSchemaViolations(invalidDDIDocument);

        // There should be 5 schema violations.
        assertEquals(5, schemaViolations.size());
    }

    @Test
    void shouldThrowOnFatalError() {
        var emptyStream = InputStream.nullInputStream();

        // Attempting to parse an empty stream should throw an exception.
        assertThrows(SAXException.class, () -> DDIVersion.DDI_2_5.getSchemaValidator().getSchemaViolations(emptyStream));
    }
}
