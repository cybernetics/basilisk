/*
 * Copyright 2008-2016 the original author or authors.
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
package basilisk.core.env;

import java.util.Locale;

import static basilisk.util.BasiliskNameUtils.capitalize;

/**
 * Defines the names of the lifecycle scripts.
 *
 * @author Andres Almiray
 */
public enum Lifecycle {
    INITIALIZE, STARTUP, READY, SHUTDOWN, STOP;

    /**
     * Display friendly name
     */
    private String name;

    /**
     * Returns the capitalized String representation of this Lifecycle object.
     *
     * @return a capitalized String
     */
    public String getName() {
        if (name == null) {
            return capitalize(this.toString().toLowerCase(Locale.getDefault()));
        }
        return name;
    }
}