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
