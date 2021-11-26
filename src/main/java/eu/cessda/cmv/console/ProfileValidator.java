package eu.cessda.cmv.console;

import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.ValidationService;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.gesis.commons.resource.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileValidator {
    private final ValidationService.V10 validationService = new CessdaMetadataValidatorFactory().newValidationService();
    private final ConcurrentHashMap<URI, Resource> profileMap = new ConcurrentHashMap<>();

    public ValidationReportV0 validateAgainstProfile(InputStream inputStream, URI profileURI, ValidationGateName validationGate) {
        // Load the DDI profile
        var profile = profileMap.computeIfAbsent(profileURI, u -> {
            try {
                return Resource.newResource(u.toURL().openStream());
            } catch (IOException e) {
                throw new ProfileLoadFailedException(e);
            }
        });

        var document = Resource.newResource(inputStream);

        return validationService.validate(document, profile, validationGate);
    }
}
