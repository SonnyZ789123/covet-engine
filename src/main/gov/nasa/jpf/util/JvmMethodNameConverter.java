/*
 * Copyright (C) 2025-2026 Yoran Mertens
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package gov.nasa.jpf.util;

public final class JvmMethodNameConverter {

    private JvmMethodNameConverter() {
    }

    /**
     * Converts:
     * com/finscore/core/engine/RuleEngine.overrides(...)
     * ->
     * com.finscore.core.engine.RuleEngine.overrides(...)
     */
    public static String toDottedClassName(String jvmName) {
        int methodSeparator = jvmName.indexOf('.');
        if (methodSeparator < 0) {
            throw new IllegalArgumentException("Invalid JVM method name: " + jvmName);
        }

        String classPart = jvmName.substring(0, methodSeparator);
        String rest = jvmName.substring(methodSeparator);

        return classPart.replace('/', '.') + rest;
    }

    /**
     * Converts:
     * com.finscore.core.engine.RuleEngine.overrides(...)
     * ->
     * com/finscore/core/engine/RuleEngine.overrides(...)
     */
    public static String toInternalClassName(String dottedName) {
        int methodSeparator = dottedName.indexOf('.');
        if (methodSeparator < 0) {
            throw new IllegalArgumentException("Invalid dotted method name: " + dottedName);
        }

        String classPart = dottedName.substring(0, methodSeparator);
        String rest = dottedName.substring(methodSeparator);

        return classPart.replace('.', '/') + rest;
    }
}
