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
package basilisk.core.editors;

/**
 * @author Andres Almiray
 */
public class ValueConversionException extends IllegalArgumentException {
    private static final long serialVersionUID = 6344566641106178891L;

    private Object value;
    private Class<?> type;

    public ValueConversionException(Object value) {
        this(value, (Exception) null);
    }

    public ValueConversionException(Object value, Class<?> type) {
        this(value, type, null);
    }

    public ValueConversionException(Object value, Class<?> type, Exception cause) {
        super("Can't convert '" + value + "' into " + type.getName(), cause);
        this.value = value;
        this.type = type;
    }

    public ValueConversionException(Object value, Exception cause) {
        super("Can't convert '" + value + "'", cause);
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public Class<?> getType() {
        return type;
    }
}
