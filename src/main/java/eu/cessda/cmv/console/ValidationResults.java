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

import eu.cessda.cmv.core.mediatype.validationreport.ValidationReport;
import org.xml.sax.SAXParseException;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static eu.cessda.cmv.console.PIDValidationResult.EMPTY_PID_REPORT;

record ValidationResults(
    State state,
    URI documentURL,
    List<SAXParseException> schemaViolations,
    PIDValidationResult pidValidationResult,
    ValidationReport report
) {
    static final ValidationReport EMPTY_VALIDATION_REPORT = new ValidationReport();

    ValidationResults(State state, URI documentURL) {
        this(state, documentURL, Collections.emptyList(), EMPTY_PID_REPORT, EMPTY_VALIDATION_REPORT);
    }
}
