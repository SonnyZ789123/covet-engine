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

package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.blockmap.model.BlockMapDTO;
import com.kuleuven.blockmap.model.MethodBlockMapDTO;
import gov.nasa.jpf.util.JvmMethodNameConverter;

import java.util.HashMap;
import java.util.Map;

public class BlockMapCoverage {

    private final BlockMapDTO blockMap;
    private final Map<String, MethodBlockMapCoverage> methodToBlockMapCoverage = new HashMap<>();

    public BlockMapCoverage(BlockMapDTO blockMap) {
        this.blockMap = blockMap;

        for (MethodBlockMapDTO methodCoverage : blockMap.methodBlockMaps) {
            // JDart works with dot notation for method full names
            String jdartFullName = JvmMethodNameConverter.toDottedClassName(methodCoverage.fullName);

            methodToBlockMapCoverage.put(jdartFullName, new MethodBlockMapCoverage(methodCoverage));
        }
    }

    public MethodBlockMapCoverage getMethodBlockMapCoverage(String methodFullName) {
        return methodToBlockMapCoverage.get(methodFullName);
    }
}
