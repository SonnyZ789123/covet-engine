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

public final class DecisionData extends NodeData {
    private final Node node;
    private final Instruction branchInsn;
    private final InstructionBranch[] nextInstructions;
    private final Node[] children;
    private final int branchWidth;
    private int numOpen;
    private int numUnexhausted;


    public DecisionData(Node node, Instruction branchInsn, InstructionBranch[] nextInstructions) {
        this.node = node;
        this.branchInsn = branchInsn;
        this.nextInstructions = nextInstructions;
        this.branchWidth = nextInstructions.length;
        this.children = new Node[branchWidth];
        this.numUnexhausted = branchWidth;
        this.numOpen = branchWidth;
    }

    public int getBranchWidth() {
        return branchWidth;
    }

    public Instruction getBranchInstruction() {
        return branchInsn;
    }

    public int getNumOpen() {
        return numOpen;
    }

    public int getNumUnexhausted() {
        return numUnexhausted;
    }

    public Node[] getChildren() {
        return children;
    }

    public Instruction getNextInstruction(int idx) {
        return nextInstructions[idx].getInstruction();
    }

    public Expression<Boolean> getConstraint(int idx) {
        return nextInstructions[idx].getConstraint();
    }

    public boolean hasChild(int idx) {
        return (children[idx] != null);
    }

    public Node getChild(int idx) {
        return children[idx];
    }

    public Node getOrCreateChild(int idx) {
        if(!hasChild(idx)) {
            children[idx] = new Node(node, nextInstructions[idx]);
        }
        return children[idx];
    }

    public boolean hasUnexhausted() {
        return (numUnexhausted > 0);
    }

    public boolean hasOpen() {
        return (numOpen > 0);
    }

    public int nextOpenChild() {
        if (!hasOpen())
            return -1;

        for(int i = 0; i < branchWidth; i++) {
            Node n = children[i];
            if(n == null || n.isOpen())
                return i;
        }

        return -1;
    }

    public void decrementOpen() {
        numOpen--;
    }

    public void decrementUnexhausted() {
        numUnexhausted--;
    }

    public void verifyDecision(Instruction branchInsn, InstructionBranch[] nextInstructions) {
        if (branchInsn != this.branchInsn)
            throw new IllegalStateException("Same decision, but different branching instruction!");
        if (nextInstructions != null && nextInstructions.length == this.getBranchWidth())
            throw new IllegalStateException("Same decision, but different number of constraints!");
    }
}
