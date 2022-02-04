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
