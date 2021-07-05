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

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.cessda.cmv.core.ValidationGateName;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public class Repository {
    private final String code;
    private final URI profile;
    private final Path directory;
    private final ValidationGateName validationGate;

    public Repository(
        @JsonProperty("code") String code,
        @JsonProperty("profile") URI profile,
        @JsonProperty("directory") Path directory,
        @JsonProperty("validationGate") ValidationGateName validationGate
    ) {
        this.code = code;
        this.profile = profile;
        this.directory = directory;
        this.validationGate = validationGate;
    }

    public String getCode() {
        return code;
    }

    public URI getProfile() {
        return profile;
    }

    public Path getDirectory() {
        return directory;
    }

    public ValidationGateName getValidationGate() {
        return validationGate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Repository that = (Repository) o;
        return Objects.equals(code, that.code) &&
            Objects.equals(profile, that.profile) &&
            Objects.equals(directory, that.directory) &&
            validationGate == that.validationGate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, profile, directory, validationGate);
    }

    @Override
    public String toString() {
        return "Repository{" +
            "code='" + code + '\'' +
            ", profile=" + profile +
            ", directory=" + directory +
            ", validationGate=" + validationGate +
            '}';
    }
}
