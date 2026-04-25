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

package gov.nasa.jpf.jdart.constraints.tree;

import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.vm.Instruction;

public class InstructionBranch {
    /** The instruction to execute for this branch. May be null. */
    private final Instruction instruction;
    private final Expression<Boolean> constraint;

    public InstructionBranch(Instruction instruction, Expression<Boolean> constraint) {
        this.instruction = instruction;
        this.constraint = constraint;
    }

    public Instruction getInstruction() {
        return instruction;
    }

    public Expression<Boolean> getConstraint() {
        return constraint;
    }
}
