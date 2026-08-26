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
