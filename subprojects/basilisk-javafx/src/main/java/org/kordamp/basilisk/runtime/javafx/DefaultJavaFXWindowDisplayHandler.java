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
package org.kordamp.basilisk.runtime.javafx;

import basilisk.javafx.JavaFXWindowDisplayHandler;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.annotation.Nonnull;

import static basilisk.util.BasiliskNameUtils.requireNonBlank;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@code WindowDisplayHandler} that simply makes the window
 * visible on show() and disposes it on hide().
 *
 * @author Andres Almiray
 */
public class DefaultJavaFXWindowDisplayHandler implements JavaFXWindowDisplayHandler {
    private static final String ERROR_NAME_BLANK = "Argument 'name' must not be null";
    private static final String ERROR_WINDOW_NULL = "Argument 'window' must not be null";

    public void show(@Nonnull String name, @Nonnull Window window) {
        requireNonBlank(name, ERROR_NAME_BLANK);
        requireNonNull(window, ERROR_WINDOW_NULL);
        if (window instanceof Stage) {
            ((Stage) window).show();
        }
    }

    public void hide(@Nonnull String name, @Nonnull Window window) {
        requireNonBlank(name, ERROR_NAME_BLANK);
        requireNonNull(window, ERROR_WINDOW_NULL);
        if (window instanceof Stage) {
            window.hide();
        }
    }
}