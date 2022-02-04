package eu.cessda.cmv.console;

import java.util.List;

record PIDValidationResult(boolean valid, List<PID> invalidPIDs) {
}
