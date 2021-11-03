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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import eu.cessda.cmv.core.CessdaMetadataValidatorFactory;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.ValidationService;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.gesis.commons.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.value;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class Validator {

    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    private static final String MDC_KEY = "validator_job";
    private static final String REPO_NAME = "repo_name";

    private final ValidationService.V10 validationService = new CessdaMetadataValidatorFactory().newValidationService();
    private final Configuration configuration;
    private final ObjectMapper objectMapper;

    // Schema validators are not thread safe, so assign each thread its own validator.
    private final ThreadLocal<javax.xml.validation.Validator> validatorThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            var resource = this.getClass().getResource("/schemas/codebook.xsd");
            var schema = SchemaFactory.newDefaultInstance().newSchema(resource);
            var validator = schema.newValidator();
            validator.setErrorHandler(new LoggingErrorHandler());
            return validator;
        } catch (SAXException e) {
            throw new IllegalStateException(e);
        }
    });

    public Validator(Configuration configuration) {
        this.configuration = configuration;
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) throws IOException {

        var configuration = parseConfiguration();

        // Allow the base path to be configurable as a program argument
        if (args.length > 0) {
            Path baseDirectory;
            Path destinationDirectory = configuration.destinationDirectory();
            Path wrappedDirectory = configuration.wrappedDirectory();
            try {
                baseDirectory = Path.of(args[0]);
                if (args.length > 1) {
                    destinationDirectory = Path.of(args[1]);
                    if (args.length > 2) {
                        wrappedDirectory = Path.of(args[2]);
                    }
                }
            } catch (InvalidPathException e) {
                log.error("Parsing base directory failed: {}", e.getMessage());
                System.exit(1);
                return;
            }
            configuration = new Configuration(baseDirectory, destinationDirectory, wrappedDirectory, configuration.repositories());
        }

        // Instance and run the validator
        var timestamp = OffsetDateTime.now().toString();
        MDC.put(MDC_KEY, timestamp);
        new Validator(configuration).validate(timestamp);
    }

    /**
     * Parse configuration from {@code configuration.yaml}.
     *
     * @throws IOException if an IO error occurs when reading the configuration.
     */
    private static Configuration parseConfiguration() throws IOException {
        return new YAMLMapper().readValue(
            Validator.class.getClassLoader().getResourceAsStream("configuration.yaml"),
            Configuration.class
        );
    }

    /**
     * Validate the given document using the specified profile and validation gate.
     *
     * @param documentPath   the document to validate.
     * @param profile        the profile to validate with.
     * @param validationGate the {@link ValidationGateName} to use.
     * @return a {@link Map.Entry} with the key set to the URL decoded file name, and the value set to the validation results.
     * @throws RuntimeException if an error occurs during the validation.
     * @throws SAXException     if the document doesn't conform to the DDI schema.
     * @throws IOException      if an IO error occurred.
     */
    private Map.Entry<Path, ValidationResults> validateDocuments(
        Path documentPath, Resource profile, ValidationGateName validationGate
    ) throws IOException, SAXException {
        var buffer = Files.readAllBytes(documentPath);

        log.debug("Validating {} against XML schema", documentPath);
        validatorThreadLocal.get().validate(new StreamSource(new ByteArrayInputStream(buffer)));
        var errors = ((LoggingErrorHandler) validatorThreadLocal.get().getErrorHandler()).getErrors();
        ((LoggingErrorHandler) validatorThreadLocal.get().getErrorHandler()).reset();

        log.debug("Validating {} with profile {}.", documentPath, profile);
        var document = Resource.newResource(new ByteArrayInputStream(buffer));
        ValidationReportV0 validationReport = validationService.validate(document, profile, validationGate);

        return Map.entry(documentPath, new ValidationResults(errors, validationReport));
    }

    /**
     * Validate all configured repositories.
     */
    private void validate(String timestamp) {
        for (var repo : configuration.repositories()) {
            log.info("{}: Performing validation.", repo.code());

            var sourceDirectory = configuration.rootDirectory().resolve(repo.directory()).normalize();

            try (var sourceFilesStream = Files.walk(sourceDirectory)) {
                var profile = Resource.newResource(repo.profile().toURL().openStream());

                var recordCounter = new AtomicInteger();
                var invalidRecordsCounter = new AtomicInteger();

                sourceFilesStream.filter(Files::isRegularFile)
                    .toList() // Collecting to a list allows better parallelisation behavior as the overall size is known
                    .parallelStream()
                    .flatMap(file -> {
                        try {
                            MDC.put(MDC_KEY, timestamp);
                            return Stream.of(validateDocuments(file, profile, repo.validationGate()));
                        } catch (RuntimeException | IOException | SAXException | OutOfMemoryError e) {
                            // Handle unexpected exceptions and out of memory errors
                            log.error("{}: Validation of {} failed", repo.code(), file, e);
                        }
                        return Stream.empty();
                    })
                    .forEach(report -> {
                        recordCounter.incrementAndGet();

                        var schemaViolations = report.getValue().schemaViolations();
                        var constraintViolations = report.getValue().report().getConstraintViolations();

                        if (!constraintViolations.isEmpty() || !schemaViolations.isEmpty()) {
                            invalidRecordsCounter.incrementAndGet();
                            try {
                                MDC.put(MDC_KEY, timestamp);
                                var fileName = URLDecoder.decode(removeExtension(report.getKey().getFileName().toString()), UTF_8);
                                var schemaViolationsString = objectMapper.writeValueAsString(schemaViolations.stream().map(SAXParseException::toString).toList());
                                var constraintViolationsString = objectMapper.writeValueAsString(constraintViolations);
                                log.info("{}: {} has {} schema and {} profile violations{}{}{}{}.",
                                    value(REPO_NAME, repo.code()),
                                    value("oai_record", fileName),
                                    schemaViolations.size(),
                                    constraintViolations.size(),
                                    keyValue("profile_name", repo.profile(), ""),
                                    keyValue("validation_gate", repo.validationGate(), ""),
                                    keyValue("schema_violations", schemaViolationsString, ""),
                                    keyValue("validation_results", constraintViolationsString, "")
                                );
                            } catch (JsonProcessingException | OutOfMemoryError e) {
                                log.error("{}: Failed to write report for {}.", repo.code(), report.getKey(), e);
                            }
                        } else {
                            if (configuration.destinationDirectory() != null) {
                                copyToDestination(repo, report.getKey());
                            }
                        }
                    });

                log.info("{}: {}: Validated {} records, {} invalid",
                    value(REPO_NAME, repo.code()),
                    value("profile_name", repo.profile()),
                    value("validated_records", recordCounter),
                    value("invalid_records", invalidRecordsCounter)
                );
            } catch (IOException | OutOfMemoryError e) {
                log.error("Failed to validate {}: {}", repo.code(), e.toString());
            }
        }
    }

    private void copyToDestination(Repository repo, Path validationPath) {
        // Convert the absolute path into a relative path from the root XML directory.
        var relativePath = configuration.rootDirectory().relativize(validationPath);

        // If an unwrapped source directory is configured use that, otherwise use the given path.
        Path sourcePath;
        if (configuration.wrappedDirectory() != null) {
            sourcePath = configuration.wrappedDirectory().resolve(relativePath);
        } else {
            sourcePath = validationPath;
        }

        var destinationPath = configuration.destinationDirectory().resolve(relativePath);
        try {
            // Create all required directories and copy the file
            Files.createDirectories(destinationPath.getParent());
            Files.copy(sourcePath, destinationPath, REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("{}: Error when copying to destination directory: {}", repo.code(), e.toString());
        }
    }
}
