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

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * An error handler that stores all encountered SAX errors and warnings.
 * <p>
 * This error handler is stateful and must be reset before validating the next document.
 *
 * @implNote Fatal errors rethrown as is and are not reported by this error handler.
 */
class LoggingErrorHandler implements ErrorHandler {
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
