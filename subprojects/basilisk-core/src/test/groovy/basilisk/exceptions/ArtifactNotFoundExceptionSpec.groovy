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
package basilisk.exceptions

import basilisk.core.artifact.BasiliskController
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ArtifactNotFoundExceptionSpec extends Specification {
    @Shared private Exception cause = new Exception()

    void 'Exception should have proper formatted message'() {
        setup:
        ArtifactNotFoundException e = new ArtifactNotFoundException(*args)

        expect:
        e.message.contains(message)

        where:
        args                        | message
        [cause]                     | 'Could not find artifact'
        [Object]                    | 'Could not find artifact for java.lang.Object'
        [BasiliskController, 'foo'] | 'Could not find artifact of type basilisk.core.artifact.BasiliskController named foo'
    }
}
