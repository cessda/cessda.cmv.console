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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.cessda.cmv.core.NotDocumentException;
import eu.cessda.cmv.core.ValidationGateName;
import eu.cessda.cmv.core.mediatype.validationreport.ValidationReport;
import org.apache.commons.cli.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static eu.cessda.cmv.console.ValidationResults.EMPTY_VALIDATION_REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.value;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class Validator {

    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    // Logging constants
    private static final String MDC_KEY = "validator_job";
    private static final String REPO_NAME = "repo_name";
    private static final String OAI_RECORD = "oai_record";
    private static final String RECORDS_DELETED_LOG_TEMPLATE = "{}: {} orphaned records deleted";

    private static final String OAI_NAMESPACE_URI = "http://www.openarchives.org/OAI/2.0/";

    // Executor for validations
    private static final Executor executor = Executors.newWorkStealingPool();


    private final Configuration configuration;
    private final ObjectMapper objectMapper;

    private final PIDValidator pidValidator = new PIDValidator();
    private final ProfileValidator profileValidator = new ProfileValidator();
    private final SchemaValidator schemaValidator = new SchemaValidator();


    public Validator(Configuration configuration, ObjectMapper objectMapper) throws SAXException {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) throws IOException, ParseException, SAXException {

        // Command line options
        var destinationOption = new Option("d", "destination", true, "The destination directory to store validated records");

        var options = new Options();
        options.addOption(destinationOption);

        // Command line parser
        var commandLine = new DefaultParser().parse(options, args);

        // Exit if the command line argument list is empty
        if (commandLine.getArgList().isEmpty()) {
            new HelpFormatter().printHelp("validator <baseDirectory>", options, true);
            System.exit(1);
        }

        // Parse the first argument as the base path
        var baseDirectory = Path.of(commandLine.getArgList().getFirst());

        // Optional configuration
        Path destinationDirectory = null;

        // Iterate through options and extract paths
        for (var option : (Iterable<Option>) commandLine::iterator) {
            if (destinationOption.equals(option)) {
                destinationDirectory = Path.of(option.getValue());
            }
        }

        // Instance the validator
        var objectMapper = new ObjectMapper();
        var validator = new Validator(
            new Configuration(baseDirectory, destinationDirectory),
            objectMapper
        );

        // Set the job ID from the current time
        var timestamp = OffsetDateTime.now().toString();

        // Discover repositories from instances of pipeline.json
        MDC.put(MDC_KEY, timestamp);

        // Initialise the directory walker
        var walker = new DirectoryWalker(objectMapper, validator, timestamp);
        var completableFutures = walker.walkDirectory(baseDirectory);
        completableFutures.forEach(CompletableFuture::join);
    }

    static class DirectoryWalker {

        private final ObjectMapper objectMapper;
        private final Validator validator;
        private final String timestamp;

        DirectoryWalker(ObjectMapper objectMapper, Validator validator, String timestamp) {
            this.objectMapper = objectMapper;
            this.validator = validator;
            this.timestamp = timestamp;
        }

        List<CompletableFuture<Void>> walkDirectory(Path directory) throws IOException {
            var validationOperations = new ArrayList<CompletableFuture<Void>>();

            try (var directoryStream = Files.newDirectoryStream(directory)) {
                // Get entry
                for (var entry : directoryStream) {
                    if (Files.isDirectory(entry)) {

                        // Recurse, search the directory
                        var recursedValidationOperations = walkDirectory(entry);
                        validationOperations.addAll(recursedValidationOperations);

                    } else if (entry.getFileName().equals(Path.of("pipeline.json"))) {

                        // Start a validation
                        parseRepositoryConfiguration(entry);

                    }
                }
            } catch (DirectoryIteratorException | IOException e) {
                log.error("Couldn't discover repositories at {}: {}", directory, e.toString());
            }

            return validationOperations;
        }

        private void parseRepositoryConfiguration(Path entry) {
            // Parse the repository information and start a validation
            try (var inputStream = Files.newInputStream(entry)) {
                var repository = objectMapper.readValue(inputStream, Repository.class);
                validator.validateRepository(entry.getParent(), repository, timestamp);
            } catch (IOException e) {

                // Failed to start validation, log and return an empty future
                log.error("Couldn't load pipeline configuration at {}: {}", entry, e.toString());
            }
        }
    }

    /**
     * Validate the given document using the specified profile and validation gate.
     *
     * @param documentPath   the document to validate.
     * @param ddiVersion     the DDI version of the document.
     * @param profile        the profile to validate with.
     * @param validationGate the {@link ValidationGateName} to use.
     * @return a {@link Map.Entry} with the key set to the file name, and the value set to the validation results.
     * @throws RuntimeException if an error occurs during the validation.
     * @throws SAXException     if the document doesn't conform to the DDI schema.
     * @throws IOException      if an IO error occurred.
     */
    ValidationResults validateDocuments(
        Path documentPath, DDIVersion ddiVersion, URI profile, ValidationGateName validationGate
    ) throws IOException, SAXException, NotDocumentException {
        State validationState = State.VALID;

        var buffer = new ByteArrayInputStream(Files.readAllBytes(documentPath));

        // Validate against XML schema, parse to DOM document
        log.debug("Validating {} against XML schema", documentPath);
        var schemaValidatorResult = schemaValidator.getSchemaViolations(buffer);

        // Reset the buffer
        buffer.reset();

        // Extract the request URL from the document
        var requestURL = extractURL(schemaValidatorResult.document());

        // Determine if this is a deleted record, skip if so
        if (isDeletedRecord(schemaValidatorResult.document())) {
            return new ValidationResults(State.SKIP, requestURL);
        }

        // Validate persistent identifiers
        PIDValidationResult pidValidationResult;
        try {
            pidValidationResult = pidValidator.validatePIDs(schemaValidatorResult.document(), ddiVersion);
        } catch (XPathExpressionException e) {
            pidValidationResult = null;
            log.error("PID validation of {} failed: {}", documentPath, e.toString());
        }


        // Validate against CMV profile
        final ValidationReport validationReport;
        if (validationGate != null && profile != null) {
            log.debug("Validating {} against CMV profile {}", documentPath, profile);
            validationReport = profileValidator.validateAgainstProfile(buffer, profile, validationGate);
            if (!validationReport.getConstraintViolations().isEmpty()) {
                validationState = State.INVALID;
            }
        } else {
            log.debug("CMV profile validation disabled for {}", documentPath);
            validationReport = EMPTY_VALIDATION_REPORT;
        }

        return new ValidationResults(
            validationState,
            requestURL,
            schemaValidatorResult.schemaViolations(),
            pidValidationResult,
            validationReport
        );
    }

    private boolean isDeletedRecord(Document document) {
        var documentElement = document.getDocumentElement();
        if (OAI_NAMESPACE_URI.equals(documentElement.getNamespaceURI())) {

            var getRecord = getElementByTagName(documentElement, "GetRecord");
            if (getRecord != null) {

                var record = getElementByTagName(getRecord, "record");
                if (record != null) {

                    var header = getElementByTagName(record, "header");
                    if (header != null) {

                        var status = header.getAttribute("status");
                        return "deleted".equalsIgnoreCase(status);
                    }
                }
            }
        }

        return false;
    }

    static URI extractURL(Document document) {
        // Only attempt extraction if the document element namespace is an OAI-PMH response
        if (OAI_NAMESPACE_URI.equals(document.getDocumentElement().getNamespaceURI())) {
            var requestElement = getElementByTagName(document.getDocumentElement(), "request");
            if (requestElement != null) {
                try {
                    return new URI(requestElement.getTextContent().trim());
                } catch (URISyntaxException e) {
                    // ignore
                }
            }
        }

        return null;
    }

    private static Element getElementByTagName(Element sourceElement, String localName) {
        var elements = sourceElement.getElementsByTagNameNS(OAI_NAMESPACE_URI, localName);
        if (elements.getLength() != 0) {
            return (Element) elements.item(0);
        } else {
            return null;
        }
    }

    private static void configureMDC(String timestamp) {
        if (MDC.get(MDC_KEY) == null) {
            MDC.put(MDC_KEY, timestamp);
        }
    }

    /**
     * Validate all configured repositories.
     */
    @SuppressWarnings("java:S2629")
    private void validateRepository(Path repoPath, Repository repo, String timestamp) {
        configureMDC(timestamp);

        log.info("{}: Performing validation.", value(REPO_NAME, repo.code()));

        // Create a stream of all XML files in the repository directory
        try (var sourceFilesStream = Files.newDirectoryStream(repoPath, Validator::xmlPathFilter)) {

            var recordCounter = new AtomicInteger();
            var invalidRecordsCounter = new AtomicInteger();

            // Each validation is scheduled to run asynchronously whilst files are being discovered.
            var futures = StreamSupport.stream(sourceFilesStream.spliterator(), false)
                .map(file -> CompletableFuture.supplyAsync(() -> {
                        // Configure the MDC context
                        configureMDC(timestamp);
                        return validateFile(repo, file, recordCounter, invalidRecordsCounter);
                    },
                executor
            )).toList();

            if (configuration.destinationDirectory() != null) {
                // Get a HashSet of copied files
                var copiedFiles = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .map(Path::getFileName)
                    .collect(Collectors.toCollection(HashSet::new));

                // Clean up files in the destination directory.
                deleteOrphanedRecords(repo, repoPath, copiedFiles);
            } else {
                // Join all the futures and wait for their completion
                futures.forEach(CompletableFuture::join);
            }

            log.info("{}: {}: Validated {} records, {} invalid",
                value(REPO_NAME, repo.code()),
                value("profile_name", repo.profile()),
                value("validated_records", recordCounter),
                value("invalid_records", invalidRecordsCounter)
            );
        } catch (DirectoryIteratorException | IOException | ProfileLoadFailedException | CompletionException e) {
            log.error("Failed to validate {}: {}", value(REPO_NAME, repo.code()), e.toString());
        }
    }

    /**
     * Validate a document.
     *
     * @param repo the source repository.
     * @param file the document.
     * @param recordCounter total records (both valid and invalid).
     * @param invalidRecordsCounter total invalid records.
     * @return the document path if the document should be copied, or {@code null} if not.
     */
    @SuppressWarnings("ErrorNotRethrown")
    private Path validateFile(Repository repo, Path file, AtomicInteger recordCounter, AtomicInteger invalidRecordsCounter) {

        // Validate the file
        try {
            // Validate the document
            var report = validateDocuments(file, repo.ddiVersion(), repo.profile(), repo.validationGate());

            if (report.state() == State.SKIP) {
                return null;
            }

            // Increment the amount of records validated
            recordCounter.incrementAndGet();

            // Report any errors
            if (!report.schemaViolations().isEmpty()
                || !report.report().getConstraintViolations().isEmpty()
                || (report.pidValidationResult() != null && !report.pidValidationResult().valid())
            ) {
                // Derive the identifier from the file name
                var recordIdentifier = URLDecoder.decode(removeExtension(file.getFileName().toString()), UTF_8);
                reportViolations(repo, recordIdentifier, report);
            }

            // Only constraint violations block copying
            switch (report.state()) {
                case VALID -> {
                    if (configuration.destinationDirectory() != null) {
                        return copyToDestination(file);
                    }
                }
                case INVALID -> invalidRecordsCounter.incrementAndGet();
            }

        } catch (NotDocumentException | IOException | SAXException | OutOfMemoryError e) {
            // Handle unexpected exceptions and out of memory errors
            log.error("{}: Validation of {} failed", value(REPO_NAME, repo.code()), file, e);

            // Increment counters
            recordCounter.incrementAndGet();
            invalidRecordsCounter.incrementAndGet();
        }

        // Either invalid, deleted, or an error occurred
        return null;
    }


    /**
     * Log schema and constraint violations.
     *
     * @param repo             the repository.
     * @param recordIdentifier the OAI-PMH record identifier.
     * @param report           the validation report.
     */
    @SuppressWarnings("ErrorNotRethrown")
    private void reportViolations(Repository repo, String recordIdentifier, ValidationResults report) {
        try {
            String cdcIdentifier;

            var repoURL = report.documentURL();
            if (repoURL == null) {
                repoURL = repo.url();
            }

            if (repoURL != null) {
                var cdcIdentifierString = repoURL + "-" + recordIdentifier;
                cdcIdentifier = DigestUtils.sha256Hex(cdcIdentifierString.getBytes(UTF_8));
            } else {
                cdcIdentifier = null;
            }

            var validPIDs = report.pidValidationResult().valid();

            // Persistent identifier report
            var validPIDURIs = new ArrayList<String>();
            var validPIDAgency = new ArrayList<String>();
            for (var validPID : report.pidValidationResult().validPIDs()) {
                validPIDAgency.add(validPID.agency());
                validPIDURIs.add(validPID.uri());
            }

            var invalidPIDAgency = new ArrayList<String>();
            var invalidPIDURIs = new ArrayList<String>();
            var invalidPIDState = new ArrayList<String>();
            for (var invalidPID :  report.pidValidationResult().invalidPIDs()) {
                invalidPIDAgency.add(invalidPID.agency());
                invalidPIDURIs.add(invalidPID.uri());
                invalidPIDState.add(invalidPID.state().toString());
            }

            // XSD schema violations
            var schemaViolations = report.schemaViolations();
            final String schemaViolationsString;
            if (!schemaViolations.isEmpty()) {
                schemaViolationsString = objectMapper.writeValueAsString(schemaViolations.stream().map(SAXParseException::toString).toList());
            } else {
                schemaViolationsString = null;
            }

            // CMV constraint violations
            var constraintViolations = report.report().getConstraintViolations();
            final String constraintViolationsString;
            if (!constraintViolations.isEmpty()) {
                constraintViolationsString = objectMapper.writeValueAsString(constraintViolations);
            } else {
                constraintViolationsString = null;
            }

            log.info("{}: {}\n{} schema violations\n{} profile violations\nValid PIDs: {}{}{}{}{}{}{}{}{}{}{}.",
                value(REPO_NAME, repo.code()),
                value(OAI_RECORD, recordIdentifier),
                schemaViolations.size(),
                constraintViolations.size(),
                value("valid_pids", validPIDs),
                keyValue("profile_name", repo.profile(), ""),
                keyValue("validation_gate", repo.validationGate(), ""),
                keyValue("schema_violations", schemaViolationsString, ""),
                keyValue("validation_results", constraintViolationsString, ""),
                keyValue("valid_pid_agency", validPIDAgency, ""),
                keyValue("valid_pid_uri", validPIDURIs, ""),
                keyValue("invalid_pid_agency", invalidPIDAgency, ""),
                keyValue("invalid_pid_uri", invalidPIDURIs, ""),
                keyValue("invalid_pid_state", invalidPIDState, ""),
                keyValue("cdc_identifier", cdcIdentifier, "")
            );
        } catch (JsonProcessingException | OutOfMemoryError e) {
            log.error("{}: Failed to write violation reports for {}.", value(REPO_NAME, repo.code()), value(OAI_RECORD, recordIdentifier), e);
        }
    }

    /**
     * Copy the validated record to the configured destination directory.
     * The folder structure of the source directory is kept.
     *
     * @param validationPath the validated record to copy.
     * @return the destination path, or {@code null} if the copying failed.
     */
    private Path copyToDestination(Path validationPath) {
        Path destinationPath = getDestinationPath(validationPath);

        try {
            // Create all required directories and copy the file
            Files.createDirectories(destinationPath.getParent());
            return Files.copy(validationPath, destinationPath, REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error when copying {} to destination directory: {}", validationPath, e.toString());
            return null;
        }
    }

    /**
     * Delete orphaned records from the destination directory.
     *
     * @param repository the source repository.
     * @param records    a {@link HashSet} of record paths that passed validation.
     */
    @SuppressWarnings({"java:S1141", "java:S2629"})
    private void deleteOrphanedRecords(Repository repository, Path repositoryPath, HashSet<Path> records) {
        // Derive the destination path of this repository from the repository source path
        Path destinationPath = getDestinationPath(repositoryPath);

        int filesDeleted = 0;

        DirectoryStream<Path> directoryStream;
        try {
            directoryStream = Files.newDirectoryStream(destinationPath, Validator::xmlPathFilter);
        } catch (NoSuchFileException e) {
            // Handle the case where the directory cannot be found separately from when individual files cannot be found
            log.debug("{}: Destination directory \"{}\" not found", value(REPO_NAME, repository.code()), destinationPath);
            return;
        } catch (IOException e) {
            logCleanupFailure(repository, destinationPath, e);
            return;
        }

        try {
            for (var file : directoryStream) {
                if (!records.contains(file.getFileName())) {
                    // Delete the records.
                    try {
                        Files.delete(file);
                        filesDeleted++;
                        log.debug("{}: Deleted {}", repository.code(), file);
                    } catch (IOException e) {
                        logCleanupFailure(repository, file, e);
                    }
                }
            }
        } finally {
            try {
                directoryStream.close();
            } catch (IOException e) {
                logCleanupFailure(repository, destinationPath, e);
            }
        }

        // Log if files are deleted at INFO level, always log at debug
        if (log.isDebugEnabled()) {
            log.debug(RECORDS_DELETED_LOG_TEMPLATE, value(REPO_NAME, repository.code()), filesDeleted);
        } else if (filesDeleted > 0) {
            log.info(RECORDS_DELETED_LOG_TEMPLATE, value(REPO_NAME, repository.code()), filesDeleted);
        }
    }

    private static void logCleanupFailure(Repository repository, Path file, IOException e) {
        log.warn("{}: Couldn't clean up \"{}\": {}", value(REPO_NAME, repository.code()), file, e.toString());
    }

    static boolean xmlPathFilter(Path entry) {
        return FilenameUtils.isExtension(entry.toString(), "xml");
    }

    /**
     * Get a {@link Path} that is mapped to the destination directory.
     * The original path must be relative to the root directory.
     *
     * @param repositoryPath the path to map, normalised using {@link Path#normalize()}.
     * @return the destination path.
     * @throws IllegalArgumentException if the path cannot be relativized against the root directory.
     */
    private Path getDestinationPath(Path repositoryPath) {
        // Convert the absolute path into a relative path from the root XML directory.
        var relativePath = configuration.rootDirectory().relativize(repositoryPath);

        // Use the relative path to construct the destination path
        return configuration.destinationDirectory().resolve(relativePath).normalize();
    }
}
