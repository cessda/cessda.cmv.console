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

import javax.xml.xpath.XPathExpressionException;

import static org.junit.jupiter.api.Assertions.*;

class PIDValidatorTest {
    @Test
    void shouldValidatePIDs() throws XPathExpressionException {
        var validDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/pid.xml");

        var validationResult = PIDValidator.validatePids(validDDIDocument, XPathContext.DDI_2_5);

        // Assert that no invalid PIDs were returned
        assertTrue(validationResult.valid());
        assertEquals(2, validationResult.invalidPIDs().size());
    }

    @Test
    void shouldReportInvalidPIDs() throws XPathExpressionException {
        var invalidDDIDocument = this.getClass().getResourceAsStream("/ddi_2_5/valid.xml");

        var validationResult = PIDValidator.validatePids(invalidDDIDocument, XPathContext.DDI_2_5);

        // Assert that no valid PIDs are found
        assertFalse(validationResult.valid());
        assertEquals(4, validationResult.invalidPIDs().size());

        // Assert that no PIDs have a valid URI
        assertTrue(validationResult.invalidPIDs().stream().noneMatch(pid -> pid.state().contains(PID.State.VALID_URI)));
    }
}
