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
