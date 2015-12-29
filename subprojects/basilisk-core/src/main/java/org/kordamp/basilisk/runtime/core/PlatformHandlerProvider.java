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
package org.kordamp.basilisk.runtime.core;

import basilisk.core.Configuration;
import basilisk.core.PlatformHandler;
import basilisk.exceptions.InstanceNotFoundException;
import basilisk.exceptions.TypeNotFoundException;
import basilisk.util.BasiliskApplicationUtils;
import basilisk.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Map;

import static basilisk.util.BasiliskNameUtils.isBlank;

/**
 * @author Andres Almiray
 */
public class PlatformHandlerProvider implements Provider<PlatformHandler> {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformHandlerProvider.class);

    private static final String DEFAULT_PLATFORM_HANDLER = "org.kordamp.basilisk.runtime.core.DefaultPlatformHandler";

    private static final Map<String, String> DEFAULT_PLATFORM_HANDLERS = CollectionUtils.<String, String>map()
        .e("linux", DEFAULT_PLATFORM_HANDLER)
        .e("linux64", DEFAULT_PLATFORM_HANDLER)
        .e("macosx", "org.kordamp.basilisk.runtime.core.DefaultMacOSXPlatformHandler")
        .e("macosx64", "org.kordamp.basilisk.runtime.core.DefaultMacOSXPlatformHandler")
        .e("solaris", DEFAULT_PLATFORM_HANDLER)
        .e("windows", DEFAULT_PLATFORM_HANDLER)
        .e("windows64", DEFAULT_PLATFORM_HANDLER);

    @Inject
    private Configuration configuration;

    @Override
    @SuppressWarnings("unchecked")
    public PlatformHandler get() {
        String platform = BasiliskApplicationUtils.platform;

        String handlerClassName = configuration.getAsString("platform.handler." + platform, DEFAULT_PLATFORM_HANDLERS.get(platform));
        if (isBlank(handlerClassName)) {
            handlerClassName = DEFAULT_PLATFORM_HANDLER;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using " + handlerClassName + " as PlatformHandler");
        }

        Class<PlatformHandler> handlerClass;
        try {
            handlerClass = (Class<PlatformHandler>) Class.forName(handlerClassName, true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new TypeNotFoundException(PlatformHandler.class.getName(), e);
        } catch (NoClassDefFoundError e) {
            // could be caused by a missing class in the classpath (such as com.apple.mrj.MRJAboutHandler)
            // use a dummy handler if and only if +JDK9
            if (BasiliskApplicationUtils.isJdk9) {
                return new DefaultPlatformHandler();
            }
            throw new TypeNotFoundException(PlatformHandler.class.getName(), e);
        }

        try {
            return handlerClass.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new InstanceNotFoundException(handlerClass, e);
        }
    }
}
