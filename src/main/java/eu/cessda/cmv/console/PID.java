package eu.cessda.cmv.console;

import java.util.Set;

record PID(String agency, String uri, Set<State> state) {
    enum State {
        AGENCY_PRESENT,
        AGENCY_ALLOWED_VALUE,
        VALID_URI
    }
}
