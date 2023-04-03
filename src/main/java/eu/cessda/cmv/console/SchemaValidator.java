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

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SchemaValidator {

    private final ThreadLocal<javax.xml.validation.Validator> validatorThreadLocal;

    SchemaValidator(String schemaResource) {
        validatorThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                var resource = SchemaValidator.class.getResource(schemaResource);
                var schema = SchemaFactory.newDefaultInstance().newSchema(resource);
                var validator = schema.newValidator();
                validator.setErrorHandler(new LoggingErrorHandler());
                return validator;
            } catch (SAXException e) {
                throw new IllegalStateException(e);
            }
        });

        // Validate parameters now rather than when a validation is run
        validatorThreadLocal.get();
    }

    /**
     * Validate the given XML document against the schema, reporting any schema violations found.
     *
     * @param inputStream the document to validate.
     * @return the list of validation errors encountered.
     * @throws SAXException if a parsing error occurs when validating the document.
     * @throws IOException  if an IO error occurs when validating the document.
     */
    public List<SAXParseException> getSchemaViolations(InputStream inputStream) throws SAXException, IOException {
        var validator = validatorThreadLocal.get();
        validator.validate(new StreamSource(inputStream));

        // Extract errors from the error handler, then reset the validator
        var errorHandler = (LoggingErrorHandler) validator.getErrorHandler();
        var errors = errorHandler.getErrors();
        errorHandler.reset();

        return errors;
    }

    /**
     * An error handler that stores all encountered SAX errors and warnings.
     * <p>
     * This error handler is stateful and must be reset before validating the next document.
     *
     * @implNote Fatal errors are rethrown as is and are not reported by this error handler.
     */
    private static class LoggingErrorHandler implements ErrorHandler {
        private final ArrayList<SAXParseException> errors = new ArrayList<>();

        @Override
        public void warning(SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void error(SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        /**
         * Clears the list of {@link SAXParseException}s from the handler.
         */
        public void reset() {
            errors.clear();
        }

        /**
         * Gets an unmodifiable list of the {@link SAXParseException}s encountered by the error handler.
         */
        public List<SAXParseException> getErrors() {
            return List.copyOf(errors);
        }
    }
}
