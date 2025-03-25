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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import eu.cessda.cmv.core.ValidationGateName;

import java.net.URI;

/**
 * A configuration of a remote repository.
 *
 * @param code           the short name of the repository.
 * @param ddiVersion     the DDI version of the metadata.
 * @param profile        the CMV profile to validate against.
 * @param validationGate the validation gate to use.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Repository(
    String code,
    URI url,
    DDIVersion ddiVersion,
    URI profile,
    ValidationGateName validationGate
) {
}
