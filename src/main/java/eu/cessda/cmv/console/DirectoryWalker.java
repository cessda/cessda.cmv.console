package eu.cessda.cmv.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static eu.cessda.cmv.console.Validator.REPO_NAME;

class DirectoryWalker {

    private static final Logger log = LoggerFactory.getLogger(DirectoryWalker.class);

    private static final Path PIPELINE_FILE_NAME = Path.of("pipeline.json");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    private DirectoryWalker(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    static List<CompletableFuture<Void>> walkDirectory(Path directory, ObjectMapper objectMapper, Validator validator) throws IOException {
        var walker = new DirectoryWalker(objectMapper, validator);
        return walker.walkDirectory(directory);
    }

    private List<CompletableFuture<Void>> walkDirectory(Path directory) throws IOException {
        var validationOperations = new ArrayList<CompletableFuture<Void>>();

        try (var directoryStream = Files.newDirectoryStream(directory)) {
            // Get entry
            for (var entry : directoryStream) {
                if (Files.isDirectory(entry)) {

                    // Recurse, search the directory
                    var recursedValidationOperations = walkDirectory(entry);
                    validationOperations.addAll(recursedValidationOperations);

                } else if (entry.getFileName().equals(PIPELINE_FILE_NAME)) {

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
                validator.validateRepository(entry.getParent(), repository);
            }
        } catch (IOException e) {
            // Failed to start validation, log
            log.error("Couldn't load pipeline configuration at {}: {}", entry, e.toString());
        }
    }
}
