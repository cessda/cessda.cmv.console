package eu.cessda.cmv.console;

import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.xml.sax.SAXParseException;

import java.util.List;

record ValidationResults(List<SAXParseException> schemaViolations, ValidationReportV0 report) {
}
