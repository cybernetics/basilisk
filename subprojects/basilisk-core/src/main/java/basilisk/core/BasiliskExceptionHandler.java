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
package basilisk.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static basilisk.util.BasiliskNameUtils.getShortName;
import static java.util.Arrays.asList;

/**
 * Catches and sanitizes all uncaught exceptions.
 *
 * @author Danno Ferrin
 * @author Andres Almiray
 */
public class BasiliskExceptionHandler implements ExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BasiliskExceptionHandler.class);

    private static final String[] CONFIG_OPTIONS = {
        BASILISK_FULL_STACKTRACE,
        BASILISK_EXCEPTION_OUTPUT
    };

    private static final String[] BASILISK_PACKAGES =
        System.getProperty("basilisk.sanitized.stacktraces",
            "org.codehaus.groovy.," +
                "org.kordamp.basilisk.," +
                "groovy.," +
                "java.," +
                "javax.," +
                "sun.," +
                "com.sun.,"
        ).split("(\\s|,)+");

    private static final List<CallableWithArgs<Boolean>> TESTS = new ArrayList<>();

    public static void addClassTest(CallableWithArgs<Boolean> test) {
        TESTS.add(test);
    }

    private BasiliskApplication application;

    public BasiliskExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    // @Inject
    public BasiliskExceptionHandler(BasiliskApplication application) {
        this.application = application;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Nullable
    protected BasiliskApplication getApplication() {
        return application;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        handle(e);
    }

    @Override
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public void handle(Throwable throwable) {
        try {
            sanitize(throwable);
            if (isOutputEnabled()) throwable.printStackTrace(System.err);
            LOG.error("Uncaught Exception", throwable);
            if (application != null) {
                application.getEventRouter().publishEvent("Uncaught" + getShortName(throwable.getClass()), asList(throwable));
                application.getEventRouter().publishEvent(ApplicationEvent.UNCAUGHT_EXCEPTION_THROWN.getName(), asList(throwable));
            }
        } catch (Throwable t) {
            sanitize(t);
            if (isOutputEnabled()) t.printStackTrace(System.err);
            LOG.error("An error occurred while handling uncaught exception " + throwable, t);
        }
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static Throwable sanitize(Throwable throwable) {
        try {
            if (!Boolean.getBoolean(BASILISK_FULL_STACKTRACE)) {
                deepSanitize(throwable);
            }
        } catch (Throwable t) {
            // don't let the exception get thrown out, will cause infinite looping!
        }
        return throwable;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static StackTraceElement[] sanitize(StackTraceElement[] stackTrace) {
        try {
            if (!Boolean.getBoolean(BASILISK_FULL_STACKTRACE)) {
                Throwable t = new Throwable();
                t.setStackTrace(stackTrace);
                sanitize(t);
                stackTrace = t.getStackTrace();
            }
        } catch (Throwable o) {
            // don't let the exception get thrown out, will cause infinite looping!
        }
        return stackTrace;
    }

    public static boolean isOutputEnabled() {
        return Boolean.getBoolean(BASILISK_EXCEPTION_OUTPUT);
    }

    public static void configure(Map<String, Object> config) {
        for (String option : CONFIG_OPTIONS) {
            if (config.containsKey(option)) {
                System.setProperty(option, String.valueOf(config.get(option)));
            }
        }
    }

    public static void registerExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new BasiliskExceptionHandler());
        System.setProperty("sun.awt.exception.handler", BasiliskExceptionHandler.class.getName());
    }

    public static void handleThrowable(Throwable t) {
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(
            Thread.currentThread(),
            t
        );
    }

    /**
     * Sanitize the exception and ALL nested causes
     * <p>
     * This will MODIFY the stacktrace of the exception instance and all its causes irreversibly
     *
     * @param t a throwable
     * @return The root cause exception instances, with stack trace modified to filter out groovy runtime classes
     */
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static Throwable deepSanitize(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = doSanitize(current.getCause());
        }
        return doSanitize(t);
    }

    private static Throwable doSanitize(Throwable t) {
        StackTraceElement[] trace = t.getStackTrace();
        List<StackTraceElement> newTrace = new ArrayList<>();
        for (StackTraceElement stackTraceElement : trace) {
            if (isApplicationClass(stackTraceElement.getClassName())) {
                newTrace.add(stackTraceElement);
            }
        }

        StackTraceElement[] clean = new StackTraceElement[newTrace.size()];
        newTrace.toArray(clean);
        t.setStackTrace(clean);
        return t;
    }

    private static boolean isApplicationClass(String className) {
        for (CallableWithArgs<Boolean> test : TESTS) {
            if (test.call(className)) {
                return false;
            }
        }

        for (String excludedPackage : BASILISK_PACKAGES) {
            if (className.startsWith(excludedPackage)) {
                return false;
            }
        }
        return true;
    }
}
