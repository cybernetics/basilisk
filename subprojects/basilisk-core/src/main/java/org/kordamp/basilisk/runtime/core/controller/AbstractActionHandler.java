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
package org.kordamp.basilisk.runtime.core.controller;

import basilisk.core.controller.AbortActionExecution;
import basilisk.core.controller.Action;
import basilisk.core.controller.ActionExecutionStatus;
import basilisk.core.controller.ActionHandler;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * @author Andres Almiray
 */
public class AbstractActionHandler implements ActionHandler {
    @Override
    public void update(@Nonnull Action action) {

    }

    @Override
    public void configure(@Nonnull Action action, @Nonnull Method method) {
    }

    @Nonnull
    @Override
    public Object[] before(@Nonnull Action action, @Nonnull Object[] args) {
        return args;
    }

    @Override
    public void after(@Nonnull ActionExecutionStatus status, @Nonnull Action action, @Nonnull Object[] args) {
    }

    @Override
    public boolean exception(@Nonnull Exception exception, @Nonnull Action action, @Nonnull Object[] args) {
        return false;
    }

    @Nonnull
    protected AbortActionExecution abortActionExecution() throws AbortActionExecution {
        throw new AbortActionExecution();
    }
}
