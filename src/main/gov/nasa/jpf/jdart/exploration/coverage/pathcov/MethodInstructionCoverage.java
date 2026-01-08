package gov.nasa.jpf.jdart.exploration.coverage.pathcov;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MethodInstructionCoverage {
    private static final InstructionCoverage EMPTY = InstructionCoverage.empty("<no-method>");

    private final Map<String, InstructionCoverage> instructionCoverageByMethod;

    public MethodInstructionCoverage(Map<String, List<int[]>> instructionPathsByMethod) {
        this.instructionCoverageByMethod = new HashMap<>();
        for (Map.Entry<String, List<int[]>> entry : instructionPathsByMethod.entrySet()) {
            String methodFullName = entry.getKey();
            List<int[]> instructionPaths = entry.getValue();
            InstructionCoverage instructionCoverage = new InstructionCoverage(methodFullName, instructionPaths);
            this.instructionCoverageByMethod.put(methodFullName, instructionCoverage);
        }
    }

    public InstructionCoverage getInstructionCoverage(String methodFullName) {
        InstructionCoverage cov = instructionCoverageByMethod.get(methodFullName);
        return cov != null ? cov : EMPTY;
    }
}
