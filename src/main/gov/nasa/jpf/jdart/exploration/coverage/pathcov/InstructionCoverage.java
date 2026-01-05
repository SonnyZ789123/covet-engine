package gov.nasa.jpf.jdart.exploration.coverage.pathcov;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

public class InstructionCoverage {
    private final String method;
    private final List<int[]> instructionPaths;
    private final Set<Integer> coveredInstructions;

    public InstructionCoverage(String methodFullName, List<int[]> instructionPaths) {
        this.method = methodFullName;
        this.instructionPaths = instructionPaths;
        this.coveredInstructions = new java.util.HashSet<>();
        instructionPaths.forEach(path -> {
            for (int instr : path) {
                coveredInstructions.add(instr);
            }
        });
    }

    public boolean isInstructionCovered(int instructionIndex) {
        return coveredInstructions.contains(instructionIndex);
    }

    public static InstructionCoverage empty(String methodFullName) {
        return new InstructionCoverage(methodFullName, emptyList());
    }
}
