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
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.mediatype.validationreport.v0.ValidationReportV0;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Collections.emptyList;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.value;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class Validator {

    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    private static final String MDC_KEY = "validator_job";
    private static final String REPO_NAME = "repo_name";
    private static final String DESTINATION_ARGUMENT = "destination";
    private static final String WRAPPED_ARGUMENT = "wrapped";
    private static final ValidationReportV0 EMPTY_VALIDATION_REPORT = new ValidationReportV0();

    private final Configuration configuration;
    private final ObjectMapper objectMapper;
    private final ProfileValidator profileValidator = new ProfileValidator();

    public Validator(Configuration configuration) {
        this.configuration = configuration;
        this.objectMapper = new ObjectMapper();
    }

    public static void main(String[] args) throws IOException, ParseException {

        var configuration = parseConfiguration();

        // Command line options
        var options = new Options();
        options.addOption("d", DESTINATION_ARGUMENT, true, "The destination directory to store validated records");
        options.addOption("w", WRAPPED_ARGUMENT, true, "Directory where wrapped records are stored");

        // Command line parser
        var commandLine = new DefaultParser().parse(options, args);

        // Parse the first argument as the base path
        Path baseDirectory;
        if (!commandLine.getArgList().isEmpty()) {
            baseDirectory = Path.of(commandLine.getArgList().get(0));
        } else {
            baseDirectory = configuration.rootDirectory();
        }

        Path destinationDirectory = configuration.destinationDirectory();
        Path wrappedDirectory = configuration.wrappedDirectory();

        // Iterate through options and extract paths
        for (var option : (Iterable<Option>) commandLine::iterator) {
            if (DESTINATION_ARGUMENT.equals(option.getLongOpt())) {
                destinationDirectory = Path.of(option.getValue());
            } else if (WRAPPED_ARGUMENT.equals(option.getLongOpt())) {
                wrappedDirectory = Path.of(option.getValue());
            }
        }

        // Instance and run the validator
        var timestamp = OffsetDateTime.now().toString();
        MDC.put(MDC_KEY, timestamp);
        new Validator(
            new Configuration(baseDirectory, destinationDirectory, wrappedDirectory, configuration.profiles(), configuration.repositories())
        ).validate(timestamp);
    }

    /**
     * Parse configuration from {@code configuration.yaml}.
     *
     * @throws IOException if an IO error occurs when reading the configuration.
     */
    static Configuration parseConfiguration() throws IOException {
        return new YAMLMapper().readValue(
            Validator.class.getClassLoader().getResourceAsStream("configuration.yaml"),
            Configuration.class
        );
    }

    /**
     * Validate the given document using the specified profile and validation gate.
     *
     * @param documentPath   the document to validate.
     * @param profiles       the profile to validate with.
     * @param validationGate the {@link ValidationGateName} to use.
     * @return a {@link Map.Entry} with the key set to the URL decoded file name, and the value set to the validation results.
     * @throws RuntimeException if an error occurs during the validation.
     * @throws SAXException     if the document doesn't conform to the DDI schema.
     * @throws IOException      if an IO error occurred.
     */
    Map.Entry<Path, ValidationResults> validateDocuments(
        Path documentPath, Configuration.Profile profiles, ValidationGateName validationGate
    ) throws IOException, SAXException {
        var buffer = Files.readAllBytes(documentPath);

        // Validate against XML schema if configured
        List<SAXParseException> errors;
        if (profiles.validator() != null) {
            log.debug("Validating {} against XML schema", documentPath);
            errors = profiles.validator().getSchemaViolations(new ByteArrayInputStream(buffer));
        } else {
            log.debug("XML schema validation disabled for {}", documentPath);
            errors = emptyList();
        }

        PIDValidationResult pidValidationResult;

        try {
            // Only validate PIDs if configured.
            if (profiles.xPathContext() != null) {
                pidValidationResult = PIDValidator.validatePids(new ByteArrayInputStream(buffer), profiles.xPathContext());
            } else {
                pidValidationResult = null;
            }
        } catch (XPathExpressionException e) {
            pidValidationResult = null;
            log.error(e.toString());
        }

        log.debug("Validating {} with profile {}.", documentPath, profiles.profileURI());
        ValidationReportV0 validationReport;
        if (validationGate != null) {
            validationReport = profileValidator.validateAgainstProfile(new ByteArrayInputStream(buffer), profiles.profileURI(), validationGate);
        } else {
            validationReport = EMPTY_VALIDATION_REPORT;
        }

        return Map.entry(documentPath, new ValidationResults(errors, pidValidationResult, validationReport));
    }

    /**
     * Validate all configured repositories.
     */
    private void validate(String timestamp) {
        for (var repo : configuration.repositories()) {
            log.info("{}: Performing validation.", repo.code());

            var sourceDirectory = configuration.rootDirectory().resolve(repo.directory()).normalize();

            try (var sourceFilesStream = Files.walk(sourceDirectory)) {
                var profile = configuration.profiles().get(repo.profile());

                var recordCounter = new AtomicInteger();
                var invalidRecordsCounter = new AtomicInteger();

                var copiedFiles = sourceFilesStream.filter(Files::isRegularFile)
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
                    .flatMap(report -> {
                        recordCounter.incrementAndGet();

                        // Report PID validation errors, these are informative
                        if (report.getValue().pidValidationResult() != null && !report.getValue().pidValidationResult().valid()) {
                            try {
                                var pidJson = objectMapper.writeValueAsString(report.getValue().pidValidationResult());
                                log.info("{}: {} has no valid persistent identifiers{}",
                                    value(REPO_NAME, repo.code()),
                                    value("oai_record", URLDecoder.decode(removeExtension(report.getKey().getFileName().toString()), UTF_8)),
                                    keyValue("pid_report", pidJson, "")
                                );
                            } catch (JsonProcessingException e) {
                                log.error("{}: Failed to write PID report for {}.", repo.code(), report.getKey(), e);
                            }
                        }

                        if (!report.getValue().report().getConstraintViolations().isEmpty() || !report.getValue().schemaViolations().isEmpty()) {
                            invalidRecordsCounter.incrementAndGet();
                            MDC.put(MDC_KEY, timestamp);
                            reportValidationErrors(repo, profile, report);
                        } else {
                            if (configuration.destinationDirectory() != null) {
                                return copyToDestination(report.getKey()).stream();
                            }
                        }
                        return Stream.empty();
                    }).collect(Collectors.toSet());

                // Clean up files.
                if (configuration.destinationDirectory() != null) {
                    deleteOrphanedRecords(repo, copiedFiles);
                }

                log.info("{}: {}: Validated {} records, {} invalid",
                    value(REPO_NAME, repo.code()),
                    value("profile_name", profile.profileURI()),
                    value("validated_records", recordCounter),
                    value("invalid_records", invalidRecordsCounter)
                );
            } catch (IOException | OutOfMemoryError | ProfileLoadFailedException e) {
                log.error("Failed to validate {}: {}", repo.code(), e.toString());
            }
        }
    }

    /**
     * Log schema and constraint violations.
     *
     * @param repo    the repository.
     * @param profile the CMV profile used.
     * @param report  the validation report.
     */
    private void reportValidationErrors(Repository repo, Configuration.Profile profile, Map.Entry<Path, ValidationResults> report) {
        try {

            var fileName = URLDecoder.decode(removeExtension(report.getKey().getFileName().toString()), UTF_8);

            var schemaViolations = report.getValue().schemaViolations();
            final String schemaViolationsString;
            if (!schemaViolations.isEmpty()) {
                schemaViolationsString = objectMapper.writeValueAsString(schemaViolations.stream().map(SAXParseException::toString).toList());
            } else {
                schemaViolationsString = null;
            }

            var constraintViolations = report.getValue().report().getConstraintViolations();
            final String constraintViolationsString;
            if (!constraintViolations.isEmpty()) {
                constraintViolationsString = objectMapper.writeValueAsString(constraintViolations);
            } else {
                constraintViolationsString = null;
            }

            log.info("{}: {} has {} schema and {} profile violations{}{}{}{}.",
                value(REPO_NAME, repo.code()),
                value("oai_record", fileName),
                schemaViolations.size(),
                constraintViolations.size(),
                keyValue("profile_name", profile.profileURI(), ""),
                keyValue("validation_gate", repo.validationGate(), ""),
                keyValue("schema_violations", schemaViolationsString, ""),
                keyValue("validation_results", constraintViolationsString, "")
            );
        } catch (JsonProcessingException | OutOfMemoryError e) {
            log.error("{}: Failed to write report for {}.", repo.code(), report.getKey(), e);
        }
    }

    /**
     * Copy the validated record to the configured destination directory.
     * <p>
     * If a wrapped directory is configured, the wrapped record will be copied to the destination
     * directory, otherwise the source file used will be copied. The folder structure of the source
     * directory is kept.
     *
     * @param validationPath the validated record to copy.
     * @return the destination path, or an empty {@link Optional} if the copying failed.
     */
    private Optional<Path> copyToDestination(Path validationPath) {
        // Convert the absolute path into a relative path from the root XML directory.
        var relativePath = configuration.rootDirectory().relativize(validationPath);

        // If an unwrapped source directory is configured use that, otherwise use the given path.
        Path sourcePath;
        if (configuration.wrappedDirectory() != null) {
            sourcePath = configuration.wrappedDirectory().resolve(relativePath);
        } else {
            sourcePath = validationPath;
        }

        var destinationPath = configuration.destinationDirectory().resolve(relativePath).normalize();
        try {
            // Create all required directories and copy the file
            Files.createDirectories(destinationPath.getParent());
            Files.copy(sourcePath, destinationPath, REPLACE_EXISTING);
            return Optional.of(destinationPath);
        } catch (IOException e) {
            log.error("Error when copying {} to destination directory: {}", validationPath, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Delete orphaned records from the destination directory.
     *
     * @param repository the source repository.
     * @param records    the collection of record paths that passed validation.
     */
    private void deleteOrphanedRecords(Repository repository, Collection<Path> records) {
        var destinationPath = configuration.destinationDirectory().resolve(repository.directory()).normalize();
        try (var directoryStream = Files.newDirectoryStream(destinationPath)) {
            for (var file : directoryStream) {
                if (!records.contains(file)) {
                    // Delete the records.
                    Files.delete(file);
                    log.debug("{}: Deleted {}", repository.code(), file);
                }
            }
        } catch (DirectoryIteratorException | IOException e) {
            log.warn("{}: Couldn't clean up: {}", repository.code(), e.toString());
        }
    }
}
