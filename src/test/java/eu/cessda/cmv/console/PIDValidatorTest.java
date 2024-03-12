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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PIDValidatorTest {


    @Test
    void shouldValidatePIDs() throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        var validDDIDocument = getDocumentBuilder().parse(this.getClass().getResourceAsStream("/ddi_2_5/pid.xml"));

        var validationResult = PIDValidator.validatePIDs(validDDIDocument, DDIVersion.DDI_2_5);

        // Assert that the document was treated as valid and that 2 PIDs were validated
        assertTrue(validationResult.valid());
        assertEquals(2, validationResult.invalidPIDs().size());
    }

    private static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    @Test
    void shouldReportInvalidPIDs() throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        var invalidDDIDocument = getDocumentBuilder().parse(this.getClass().getResourceAsStream("/ddi_2_5/invalid.xml"));

        var validationResult = PIDValidator.validatePIDs(invalidDDIDocument, DDIVersion.DDI_2_5);

        // Assert that no valid PIDs are found
        assertFalse(validationResult.valid());
        assertEquals(2, validationResult.invalidPIDs().size());

        // Assert that no PIDs have a valid URI
        assertTrue(validationResult.invalidPIDs().stream().noneMatch(pid -> pid.state().contains(PID.State.VALID_URI)));
    }

    @Test
    void shouldValidatePIDsInDDI3Document() throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        var validDDIDocument = getDocumentBuilder().parse(this.getClass().getResourceAsStream("/ddi_3_2/valid.xml"));

        var validationResult = PIDValidator.validatePIDs(validDDIDocument, DDIVersion.DDI_3_2);

        // Assert that the document was treated as valid and that 2 PIDs were validated
        assertTrue(validationResult.valid());
        assertEquals(2, validationResult.invalidPIDs().size());
    }

    @Test
    void shouldValidateDOI() {
        var testDOI = "10.0.120/test/doi";
        assertTrue(PIDValidator.doiStringValidator(testDOI));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "invalid.prefix/test/doi",
        "10.invalid/test/doi",
        "not-a-doi",
        "10.123"
    })
    void shouldRejectInvalidDOI(String parameter) {
        assertFalse(PIDValidator.doiStringValidator(parameter));
    }
}
