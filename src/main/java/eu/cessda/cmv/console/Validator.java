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
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.logstash.logback.argument.StructuredArguments.raw;
import static net.logstash.logback.argument.StructuredArguments.value;
import static org.apache.commons.io.FilenameUtils.removeExtension;

public class Validator {

    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    private final ValidationService.V10 validationService = new CessdaMetadataValidatorFactory().newValidationService();
    private final Configuration configuration;

    public Validator(Configuration configuration) {
        this.configuration = configuration;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        log.info("Starting validator.");

        Thread.sleep(Duration.ofSeconds(5).toMillis());

        var configuration = parseConfiguration();

        // Instance and run the validator
        new Validator(configuration).validate();
    }

    private void validate() {
        for (var repo : configuration.getRepositories()) {
            log.info("{}: Performing validation.", repo.getCode());

            var sourceDirectory = configuration.getRootDirectory().resolve(repo.getDirectory()).normalize();

            try (var sourceFilesStream = Files.walk(sourceDirectory)) {
                var profile = Resource.newResource(repo.getProfile().toURL().openStream());

                sourceFilesStream.filter(file -> !Files.isDirectory(file))
                    .collect(Collectors.toList())
                    .parallelStream()
                    .flatMap(file -> {
                        try {
                            return Stream.of(validateDocuments(file, profile, repo.getValidationGate()));
                        } catch (RuntimeException e) {
                            log.warn("Validation of {} failed", file, e);
                            return Stream.empty();
                        }
                    })
                    .forEach(report -> {
                        try {
                            var json = new ObjectMapper().writeValueAsString(report);
                            log.info("{}: {}: Validation Results: {}.",
                                value("repo_name", repo.getCode()),
                                value("oai_record", report.getKey()),
                                raw("validation_results", json)
                            );
                        } catch (JsonProcessingException e) {
                            log.error("{}: Failed to write report for {}.", repo.getCode(), report.getKey());
                        }
                    });
            } catch (IOException e) {
                log.error("Failed to harvest {}: {}", repo.getCode(), e.toString());
            }
        }
    }

    /**
     * Validate the given document using the specified profile and validation gate.
     *
     * @param documentPath   the document to validate.
     * @param profile        the profile to validate with.
     * @param validationGate the {@link ValidationGateName} to use.
     * @return a {@link Map.Entry} with the key set to the URL decoded file name, and the value set to the validation result.
     * @throws RuntimeException if an error occurs during the validation.
     */
    private Map.Entry<String, ValidationReportV0> validateDocuments(
        Path documentPath, Resource profile, ValidationGateName validationGate
    ) {
        log.debug("Validating {} with profile {}.", documentPath, profile);

        var fileName = URLDecoder.decode(removeExtension(documentPath.getFileName().toString()), UTF_8);
        var document = Resource.newResource(documentPath.toFile());

        ValidationReportV0 validationReport = validationService.validate(document, profile, validationGate);
        return Map.entry(fileName, validationReport);
    }

    private static Configuration parseConfiguration() throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());

        return mapper.readValue(
            Validator.class.getClassLoader().getResourceAsStream("configuration.yaml"),
            Configuration.class
        );
    }
}
