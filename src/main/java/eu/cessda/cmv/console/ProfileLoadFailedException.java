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

import java.io.IOException;
import java.util.Objects;

/**
 * Thrown when an IO error prevents the CMV profile from being loaded.
 */
public class ProfileLoadFailedException extends RuntimeException {
    /**
     * Constructs a new {@link ProfileLoadFailedException} with the specified cause.
     *
     * @param e the cause, must not be null.
     */
    public ProfileLoadFailedException(IOException e) {
        super(Objects.requireNonNull(e, "exception must not be null"));
    }
}
