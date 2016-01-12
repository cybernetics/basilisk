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
package org.kordamp.basilisk.runtime.core.artifact

import basilisk.core.ApplicationBootstrapper
import basilisk.core.BasiliskApplication
import basilisk.core.artifact.BasiliskArtifact
import basilisk.core.artifact.BasiliskController
import basilisk.core.artifact.BasiliskModel
import basilisk.core.artifact.BasiliskView
import basilisk.core.env.ApplicationPhase
import basilisk.core.mvc.MVCFunction
import basilisk.core.mvc.MVCGroup
import integration.SimpleController
import integration.SimpleModel
import integration.SimpleView
import integration.TestBasiliskApplication
import org.kordamp.basilisk.runtime.core.DefaultApplicationBootstrapper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.annotation.Nullable
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Stepwise
class BasiliskArtifactSpec extends Specification {
    @Shared
    private static BasiliskApplication application

    static {
        System.setProperty('org.slf4j.simpleLogger.defaultLogLevel', 'trace')
        System.setProperty('basilisk.full.stacktrace', 'true')
    }

    def setupSpec() {
        application = new TestBasiliskApplication()
    }

    def cleanupSpec() {
        //when:
        assert application.shutdown()

        // then:
        assert ApplicationPhase.SHUTDOWN == application.phase
    }

    def "Bootstrap application"() {
        given:
        ApplicationBootstrapper bootstrapper = new DefaultApplicationBootstrapper(application)

        when:
        bootstrapper.bootstrap()
        bootstrapper.run()

        then:
        ApplicationPhase.MAIN == application.phase
    }

    def 'Verify withMvcGroup(type , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple')
                checks << (controller.key == null)
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify withMvcGroup(type, id , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', 'simple-1', new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple-1')
                checks << (controller.key == null)
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify withMvcGroup(type, id, map , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', 'simple-2', [key: 'basilisk'], new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple-2')
                checks << (controller.key == 'basilisk')
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify withMvcGroup(type, map , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', [key: 'basilisk'], new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple')
                checks << (controller.key == 'basilisk')
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify withMvcGroup(map, type, id , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', 'simple-2', key: 'basilisk', new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple-2')
                checks << (controller.key == 'basilisk')
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify withMvcGroup(map, type , handler)'() {
        given:
        List checks = []

        when:
        resolveMVCHandler().withMVC('simple', key: 'basilisk', new MVCFunction() {
            void apply(
                @Nullable BasiliskModel model, @Nullable BasiliskView view, @Nullable BasiliskController controller) {
                checks << (model instanceof SimpleModel)
                checks << (view instanceof SimpleView)
                checks << (controller instanceof SimpleController)
                checks << (controller.mvcId == 'simple')
                checks << (controller.key == 'basilisk')
            }
        })

        then:
        checks.every { it == true }
    }

    def 'Verify createMVCGroup(type)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple')

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple'
        group.controller.key == null

        cleanup:
        group.destroy()
    }

    def 'Verify createMVCGroup(type, id)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple', 'simple-1')

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple-1'
        group.controller.key == null

        cleanup:
        group.destroy()
    }

    def 'Verify createMVCGroup(type, map)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple', [key: 'basilisk'])

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple'
        group.controller.key == 'basilisk'

        cleanup:
        group.destroy()
    }

    def 'Verify createMVCGroup(map, type)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple', key: 'basilisk')

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple'
        group.controller.key == 'basilisk'

        cleanup:
        group.destroy()
    }

    def 'Verify createMVCGroup(type, id, map)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple', 'simple-2', [key: 'basilisk'])

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple-2'
        group.controller.key == 'basilisk'

        cleanup:
        group.destroy()
    }

    def 'Verify createMVCGroup(map, type, id)'() {
        given:
        MVCGroup group = resolveMVCHandler().createMVCGroup('simple', 'simple-2', key: 'basilisk')

        expect:
        group.model instanceof SimpleModel
        group.view instanceof SimpleView
        group.controller instanceof SimpleController
        group.controller.mvcId == 'simple-2'
        group.controller.key == 'basilisk'

        cleanup:
        group.destroy()
    }

    def 'Verify createMVC(type)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple')

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple'
        members[2].key == null

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify createMVC(type, id)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple', 'simple-1')

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple-1'
        members[2].key == null

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify createMVC(type, map)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple', [key: 'basilisk'])

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple'
        members[2].key == 'basilisk'

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify createMVC(map, type)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple', key: 'basilisk')

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple'
        members[2].key == 'basilisk'

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify createMVC(type, id, map)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple', 'simple-2', [key: 'basilisk'])

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple-2'
        members[2].key == 'basilisk'

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify createMVC(map, type, id)'() {
        given:
        List members = resolveMVCHandler().createMVC('simple', 'simple-2', key: 'basilisk')

        expect:
        members[0] instanceof SimpleModel
        members[1] instanceof SimpleView
        members[2] instanceof SimpleController
        members[2].mvcId == 'simple-2'
        members[2].key == 'basilisk'

        cleanup:
        members[2].mvcGroup.destroy()
    }

    def 'Verify destroyMVCGroup(id)'() {
        given:
        resolveMVCHandler().createMVCGroup('simple')

        when:
        resolveMVCHandler().destroyMVCGroup('simple')

        then:
        !application.mvcGroupManager.findGroup('simple')
    }

    def 'Execute runnable inside UI sync'() {
        given:
        boolean invoked = false

        when:
        resolveMVCHandler().runInsideUISync {
            invoked = true
        }

        then:
        invoked
    }

    def 'Execute runnable inside UI async'() {
        given:
        boolean invoked = false

        when:
        resolveMVCHandler().runInsideUIAsync {
            invoked = true
        }

        then:
        invoked
    }

    def 'Execute runnable outside UI'() {
        given:
        boolean invoked = false

        when:
        resolveMVCHandler().runOutsideUI() {
            invoked = true
        }

        then:
        invoked
    }

    def 'Execute future'() {
        given:
        boolean invoked = false

        when:
        Future future = resolveMVCHandler().runFuture {
            invoked = true
        }
        future.get()

        then:
        invoked
    }

    def 'Execute future with ExecutorService'() {
        given:
        boolean invoked = false

        when:
        Future future = resolveMVCHandler().runFuture(Executors.newFixedThreadPool(1)) {
            invoked = true
        }
        future.get()

        then:
        invoked
    }

    protected BasiliskArtifact resolveMVCHandler() {
        application.mvcGroupManager.findGroup('integration').controller
    }
}
