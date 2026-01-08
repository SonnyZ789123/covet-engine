package gov.nasa.jpf.jdart.exploration.coverage.pathcov;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class InstructionCoverage {
    private final String methodFullName;
    private final BitSet covered;

    public InstructionCoverage(String methodFullName, List<int[]> paths) {
        this.methodFullName = methodFullName;
        this.covered = new BitSet();
        for (int[] path : paths) {
            for (int instr : path) {
                covered.set(instr);
            }
        }
    }

    public boolean isInstructionCovered(int instructionIndex) {
        return covered.get(instructionIndex);
    }

    public static InstructionCoverage empty(String methodFullName) {
        return new InstructionCoverage(methodFullName, emptyList());
    }
}
