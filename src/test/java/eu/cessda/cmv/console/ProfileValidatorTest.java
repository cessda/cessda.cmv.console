/*
 * Copyright © 2017-2023 CESSDA ERIC (support@cessda.eu)
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

import eu.cessda.cmv.core.ValidationGateName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileValidatorTest {
    ProfileValidator profileValidator = new ProfileValidator();

    @Test
    void shouldValidateDocument() throws URISyntaxException {
        var document = this.getClass().getResourceAsStream("/ddi_2_5/valid.xml");

        var results = profileValidator.validateAgainstProfile(
            document, this.getClass().getResource("/profiles/cdc-ddi2.5.xml").toURI(), ValidationGateName.BASIC
        );

        assertTrue(results.getConstraintViolations().isEmpty());
    }

    @Test
    void shouldThrowIfProfileCannotBeLoaded() {
        // Invalid URI.
        var uri = URI.create("invalid://test");

        // Empty input stream, should not be consumed.
        var nullInputStream = InputStream.nullInputStream();

        assertThrows(ProfileLoadFailedException.class, () ->
            profileValidator.validateAgainstProfile(nullInputStream, uri, ValidationGateName.BASIC)
        );
    }
}
