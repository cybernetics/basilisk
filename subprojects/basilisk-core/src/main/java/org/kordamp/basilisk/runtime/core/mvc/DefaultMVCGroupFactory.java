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
package org.kordamp.basilisk.runtime.core.mvc;

import basilisk.core.mvc.MVCGroup;
import basilisk.core.mvc.MVCGroupConfiguration;
import basilisk.core.mvc.MVCGroupFactory;
import basilisk.core.mvc.MVCGroupManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;

/**
 * @author Andres Almiray
 */
public class DefaultMVCGroupFactory implements MVCGroupFactory {
    @Inject
    protected MVCGroupManager mvcGroupManager;

    @Nonnull
    @Override
    public MVCGroup create(@Nonnull MVCGroupConfiguration configuration, @Nullable String mvcId, @Nonnull Map<String, Object> members, @Nullable MVCGroup parentGroup) {
        return new DefaultMVCGroup(mvcGroupManager, configuration, mvcId, members, parentGroup);
    }
}
