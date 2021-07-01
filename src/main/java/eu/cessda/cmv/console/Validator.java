package eu.cessda.cmv.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.ValidationService;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.gesis.commons.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.raw;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class Validator {

    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    private static final ValidationService.V10 validationService = new CessdaMetadataValidatorFactory().newValidationService();

    public static void main(String[] args) throws IOException {

        var configuration = parseConfiguration();

        for (var repo : configuration.getRepositories()) {
            log.info("{}: Performing validation", repo.getCode());

            var profile = Resource.newResource(repo.getProfile());

            var sourceDirectory = configuration.getRootDirectory().resolve(repo.getDirectory());

            var collect = validateDocuments(profile, sourceDirectory);
            var json = new ObjectMapper().writeValueAsString(collect);
            log.info("{}: Validation Results: {}",
                keyValue("repo_name", repo.getCode()),
                raw("validation_results", json)
            );
        }
    }

    private static Map<String, ValidationReportV0> validateDocuments(Resource profile, Path documentPath) throws IOException {
        try (var sourceFiles = Files.walk(documentPath)) {
            return sourceFiles.filter(file -> !Files.isDirectory(file))
                .map(Path::normalize)
                .map(file -> {
                    log.debug("Validating {} with profile {}", file, profile);

                    var fileName = URLDecoder.decode(removeExtension(file.getFileName().toString()), UTF_8);
                    var document = Resource.newResource(file.toFile());

                    ValidationReportV0 validationReport = validationService.validate(document, profile, ValidationGateName.BASIC);

                    return Map.entry(fileName, validationReport);
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static Configuration parseConfiguration() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());

        return mapper.readValue(
            Validator.class.getClassLoader().getResourceAsStream("configuration.yaml"),
            Configuration.class
        );
    }
}
