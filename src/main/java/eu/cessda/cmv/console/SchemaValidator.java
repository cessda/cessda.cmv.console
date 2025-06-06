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

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class SchemaValidator {
    private final ThreadLocal<DocumentBuilderHandler> documentBuilderThreadLocal;

    SchemaValidator() throws SAXException {
        // Find the resources for the XML schemas
        var schemaURLs = new URL[] {
            this.getClass().getResource("/schemas/codebook/codebook.xsd"),
            this.getClass().getResource("/schemas/lifecycle/3.2/instance.xsd"),
            this.getClass().getResource("/schemas/lifecycle/3.3/instance.xsd"),
            this.getClass().getResource("/schemas/nesstar/Version1-2-2.xsd"),
            this.getClass().getResource("/schemas/oai-pmh/OAI-PMH.xsd")
        };

        var sources = Arrays.stream(schemaURLs)
            .map(URL::toExternalForm)
            .map(StreamSource::new)
            .toArray(StreamSource[]::new);

        // Construct schema objects from the XML schemas
        var schema = SchemaFactory.newDefaultInstance().newSchema(sources);

        documentBuilderThreadLocal = ThreadLocal.withInitial(() -> {
            try {
                // Create a document builder and set the schema
                var documentBuilderFactory = DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setNamespaceAware(true);
                documentBuilderFactory.setSchema(schema);

                var documentBuilder = documentBuilderFactory.newDocumentBuilder();

                // Configure the error handler
                var errorHandler = new LoggingErrorHandler();
                documentBuilder.setErrorHandler(errorHandler);

                return new DocumentBuilderHandler(documentBuilder, errorHandler);
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException(e);
            }
        });

        // Validate parameters now rather than when a validation is run
        documentBuilderThreadLocal.get();
    }

    /**
     * Validate the given XML document against the schema, reporting any schema violations found.
     *
     * @param inputStream the document to validate.
     * @return a record containing the parsed document and the list of validation errors encountered.
     * @throws SAXException if a parsing error occurs when validating the document.
     * @throws IOException  if an IO error occurs when validating the document.
     */
    SchemaValidatorResult getSchemaViolations(InputStream inputStream) throws SAXException, IOException {
        var documentBuilder = documentBuilderThreadLocal.get();
        var document = documentBuilder.builder().parse(inputStream);

        // Extract errors from the error handler, then reset the validator
        var errorHandler = documentBuilder.errorHandler();
        var errors = errorHandler.getErrors();
        errorHandler.reset();

        return new SchemaValidatorResult(document, errors);
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

    private record DocumentBuilderHandler(
        DocumentBuilder builder,
        LoggingErrorHandler errorHandler
    ) {
    }

    record SchemaValidatorResult(
        Document document,
        List<SAXParseException> schemaViolations
    ) {
    }
}
