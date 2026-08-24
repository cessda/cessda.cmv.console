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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static eu.cessda.cmv.console.ValidationResults.EMPTY_VALIDATION_REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
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
                try (var _ = MDC.putCloseable(REPO_NAME, repository.code())) {
                    validator.validateRepository(entry.getParent(), repository, timestamp);
                }
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
     * @param profile        the profile to validate with.
     * @param validationGate the {@link ValidationGateName} to use.
     * @return a {@link Map.Entry} with the key set to the file name, and the value set to the validation results.
     * @throws RuntimeException if an error occurs during the validation.
     * @throws SAXException     if a parsing error occurs when parsing and validating the document.
     * @throws IOException      if an IO error occurred.
     */
    ValidationResults validateDocuments(
        Path documentPath, URI profile, ValidationGateName validationGate
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

        // Discover DDI version
        var ddiVersion = discoverDDIVersion(schemaValidatorResult.document());

        // Validate persistent identifiers
        PIDValidationResult pidValidationResult = null;
        if (ddiVersion != null) {
            try {
                pidValidationResult = pidValidator.validatePIDs(schemaValidatorResult.document(), ddiVersion);
            } catch (XPathExpressionException e) {
                log.error("PID validation of {} failed: {}", documentPath, e.toString());
            }
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

    private static DDIVersion discoverDDIVersion(Document document) {
        Element metadataElement = null;

        // Is this an OAI-PMH response
        if (OAI_NAMESPACE_URI.equals(document.getNamespaceURI())) {

            var record = getRecordElement(document);
            if (record != null) {

                var metadata = getOAIElementByTagName(record, "metadata");
                if (metadata != null) {
                    metadataElement = getElementByTagName(metadata, "*", "*");
                }
            }
        } else {
            metadataElement = document.getDocumentElement();
        }

        if (metadataElement != null) {
            var namespaceURI = metadataElement.getNamespaceURI();
            return DDIVersion.fromNamespace(namespaceURI);
        }

        return null;
    }

    private static boolean isDeletedRecord(Document document) {
        var documentElement = document.getDocumentElement();
        if (OAI_NAMESPACE_URI.equals(documentElement.getNamespaceURI())) {

            var record = getRecordElement(document);
            if (record != null) {

                var header = getOAIElementByTagName(record, "header");
                if (header != null) {

                    var status = header.getAttribute("status");
                    return "deleted".equalsIgnoreCase(status);
                }
            }
        }

        return false;
    }

    private static Element getRecordElement(Document document) {
        // Is this an OAI-PMH response
        var documentElement = document.getDocumentElement();

        var getRecord = getOAIElementByTagName(documentElement, "GetRecord");
        if (getRecord != null) {
            return getOAIElementByTagName(getRecord, "record");
        }

        return null;
    }

    static URI extractURL(Document document) {
        // Only attempt extraction if the document element namespace is an OAI-PMH response
        if (OAI_NAMESPACE_URI.equals(document.getDocumentElement().getNamespaceURI())) {
            var requestElement = getOAIElementByTagName(document.getDocumentElement(), "request");
            if (requestElement != null) {
                try {
                    return new URI(requestElement.getTextContent().trim());
                } catch (URISyntaxException _) {
                    // ignore
                }
            }
        }

        return null;
    }

    private static Element getOAIElementByTagName(Element sourceElement, String localName) {
        return getElementByTagName(sourceElement, OAI_NAMESPACE_URI, localName);
    }

    private static Element getElementByTagName(Element sourceElement, String namespaceURI, String localName) {
        var elements = sourceElement.getElementsByTagNameNS(namespaceURI, localName);
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

        log.info("{}: Performing validation.", repo.code());

        // Create a stream of all XML files in the repository directory
        try (var sourceFilesStream = Files.newDirectoryStream(repoPath, Validator::xmlPathFilter)) {

            var recordCounter = new AtomicInteger();
            var invalidRecordsCounter = new AtomicInteger();

            // Each validation is scheduled to run asynchronously whilst files are being discovered.
            var mdc = MDC.getCopyOfContextMap();
            var futures = new ArrayList<CompletableFuture<Path>>();
            for (Path path : sourceFilesStream) {
                var pathFuture = CompletableFuture.supplyAsync(() -> {
                        // Configure the MDC context
                        MDC.setContextMap(mdc);
                        return validateFile(repo, path, recordCounter, invalidRecordsCounter);
                    },
                    executor
                ).exceptionally(e -> {
                    log.error("{}: Validation of {} failed", repo.code(), path, e);
                    return null;
                });
                futures.add(pathFuture);
            }

            if (configuration.destinationDirectory() != null) {
                // Get a HashSet of copied files
                var copiedFiles = new HashSet<Path>();
                for (var future : futures) {
                    Path file = future.join();
                    if (file != null) {
                        copiedFiles.add(file.getFileName());
                    }
                }

                // Clean up files in the destination directory.
                deleteOrphanedRecords(repo, repoPath, copiedFiles);
            } else {
                // Join all the futures and wait for their completion
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }

            log.atInfo()
                    .addKeyValue("profile_name", repo.profile())
                    .addKeyValue("validated_records", recordCounter)
                    .addKeyValue("invalid_records", invalidRecordsCounter)
                    .log("{}: {}: Validated {} records, {} invalid",
                 repo.code(), repo.profile(), recordCounter, invalidRecordsCounter
            );
        } catch (DirectoryIteratorException | IOException | ProfileLoadFailedException e) {
            log.error("Failed to validate {}: {}", repo.code(), e.toString());
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
    private Path validateFile(Repository repo, Path file, AtomicInteger recordCounter, AtomicInteger invalidRecordsCounter) {

        // Derive the record identifier from the file name
        var recordIdentifier = URLDecoder.decode(removeExtension(file.getFileName().toString()), UTF_8);

        // Validate the file
        try (var _ = MDC.putCloseable(OAI_RECORD, recordIdentifier)) {
            // Validate the document
            var report = validateDocuments(file, repo.profile(), repo.validationGate());

            if (report.state() == State.SKIP) {
                return null;
            }

            // Report any errors
            var constraintViolations = report.report().getConstraintViolations();
            var schemaViolations = report.schemaViolations();
            var pidValidationResult = report.pidValidationResult();

            if (!schemaViolations.isEmpty()
                || !constraintViolations.isEmpty()
                || (pidValidationResult != null && !pidValidationResult.valid())
            ) {
                // Derive the identifier from the file name
                reportViolations(repo, recordIdentifier, report);
            }

            // Only constraint violations block copying
            if (report.state() == State.VALID) {
                if (configuration.destinationDirectory() != null) {
                    return copyToDestination(file);
                }
            } else if (report.state() == State.INVALID) {
                invalidRecordsCounter.incrementAndGet();
            }

        } catch (NotDocumentException | SAXException | IOException e) {
            // Handle invalid DDI document
            log.warn("{}: Validation of {} failed: {}", repo.code(), file, e.toString());

            // Increment counters
            invalidRecordsCounter.incrementAndGet();
        } finally {
            recordCounter.incrementAndGet();
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

            // PID results
            boolean validPIDs = false;
            List<String> validPIDURIs = Collections.emptyList();
            List<String> validPIDAgency = Collections.emptyList();
            List<String> invalidPIDAgency = Collections.emptyList();
            List<String> invalidPIDURIs = Collections.emptyList();
            List<String> invalidPIDState = Collections.emptyList();

            if (report.pidValidationResult() != null) {
                validPIDs = report.pidValidationResult().valid();

                // Persistent identifier report
                validPIDURIs = new ArrayList<>();
                validPIDAgency = new ArrayList<>();
                for (var validPID : report.pidValidationResult().validPIDs()) {
                    validPIDAgency.add(validPID.agency());
                    validPIDURIs.add(validPID.uri());
                }

                invalidPIDAgency = new ArrayList<>();
                invalidPIDURIs = new ArrayList<>();
                invalidPIDState = new ArrayList<>();
                for (var invalidPID : report.pidValidationResult().invalidPIDs()) {
                    invalidPIDAgency.add(invalidPID.agency());
                    invalidPIDURIs.add(invalidPID.uri());
                    invalidPIDState.add(invalidPID.state().toString());
                }
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

            log.atInfo()
                    .addKeyValue("profile_name", repo.profile())
                    .addKeyValue("validation_gate", repo.validationGate())
                    .addKeyValue("schema_violations", schemaViolationsString)
                    .addKeyValue("validation_results", constraintViolationsString)
                    .addKeyValue("valid_pid_agency", validPIDAgency)
                    .addKeyValue("valid_pid_uri", validPIDURIs)
                    .addKeyValue("invalid_pid_agency", invalidPIDAgency)
                    .addKeyValue("invalid_pid_uri", invalidPIDURIs)
                    .addKeyValue("invalid_pid_state", invalidPIDState)
                    .addKeyValue("cdc_identifier", cdcIdentifier)
                    .log("{}: {}\n{} schema violations\n{} profile violations\nValid PIDs: {}.",
                repo.code(), recordIdentifier, schemaViolations.size(), constraintViolations.size(), validPIDs
            );
        } catch (JsonProcessingException | OutOfMemoryError e) {
            log.error("{}: Failed to write violation reports for {}.", repo.code(), recordIdentifier, e);
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
        } catch (NoSuchFileException _) {
            // Handle the case where the directory cannot be found separately from when individual files cannot be found
            log.debug("{}: Destination directory \"{}\" not found", repository.code(), destinationPath);
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
            log.debug(RECORDS_DELETED_LOG_TEMPLATE, repository.code(), filesDeleted);
        } else if (filesDeleted > 0) {
            log.info(RECORDS_DELETED_LOG_TEMPLATE, repository.code(), filesDeleted);
        }
    }

    private static void logCleanupFailure(Repository repository, Path file, IOException e) {
        log.warn("{}: Couldn't clean up \"{}\": {}", repository.code(), file, e.toString());
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
     * @throws IllegalArgumentException if the path cannot be relativised against the root directory.
     */
    private Path getDestinationPath(Path repositoryPath) {
        // Convert the absolute path into a relative path from the root XML directory.
        var relativePath = configuration.rootDirectory().relativize(repositoryPath);

        // Use the relative path to construct the destination path
        return configuration.destinationDirectory().resolve(relativePath).normalize();
    }
}
