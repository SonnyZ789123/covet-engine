package gov.nasa.jpf.jdart.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.exploration.coverage.WeightedNode;
import gov.nasa.jpf.jdart.exploration.coverage.pathcov.InstructionCoverage;
import gov.nasa.jpf.jdart.exploration.coverage.pathcov.MethodInstructionCoverage;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.jdart.constraints.tree.NodeType;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;

import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.*;

public class CoverageHeuristicStrategy implements ExplorationStrategy {
    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    // TODO: use heuristic
    private static final MethodInstructionCoverage methodInstructionCoverage;

    static {
        try {
            // Adjust path as needed (absolute or relative to working dir)
            Reader reader = new FileReader("/workspace/data/jdart_instruction_paths.json");

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            Type type = new TypeToken<Map<String, List<int[]>>>() {}.getType();
            Map<String, List<int[]>> instructionPathsByMethod = gson.fromJson(reader, type);

            methodInstructionCoverage = new MethodInstructionCoverage(instructionPathsByMethod);

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final Queue<WeightedNode> nodesFrontierQueue;
    private Node previousTargetedNode;
    private InstructionCoverage currentInstructionCoverage;

    public CoverageHeuristicStrategy() {
        // Initialize a priority queue: lower weight, and lower depth have higher priority
        nodesFrontierQueue = new PriorityQueue<>(
                Comparator.comparingDouble(WeightedNode::getWeight)
                        .thenComparingInt(w -> w.getNode().getDepth())
        );
        previousTargetedNode = null;
    }

    private double computeWeight(Instruction instruction) {
        boolean instructionCovered = currentInstructionCoverage.isInstructionCovered(instruction.getInstructionIndex());
        return instructionCovered ? 1 : 0;
    }

    private void addChildren(DecisionData decisionData) {
        for (int i = 0; i < decisionData.getBranchWidth(); i++) {
            Instruction nextInstruction = decisionData.getNextInstruction(i);
            double weight = 0;
            if (nextInstruction != null) {
                weight = computeWeight(nextInstruction);
            }

            Node childNode = decisionData.getOrCreateChild(i);
            WeightedNode weightedChildNode = new WeightedNode(childNode, weight);
            nodesFrontierQueue.add(weightedChildNode);
        }
    }

    private void addChildrenOfPreviousTargetedNode() {
        if (previousTargetedNode == null) {
            return;
        }

        DecisionData decisionData = previousTargetedNode.decisionData();
        if (decisionData == null) {
            return;
        }

        addChildren(decisionData);
    }

    @Override
    public Valuation findNext(InternalConstraintsTree ctx, MethodInfo methodInfo) {
        debugLogger.finest("[findNext] entry");

        ctx.findNextInit();

        // Start of the concolic method execution
        if (previousTargetedNode == null) {
            currentInstructionCoverage = methodInstructionCoverage.getInstructionCoverage(methodInfo.getFullName());
            // Weight doesn't matter for root because it's always explored in the first run
            nodesFrontierQueue.add(new WeightedNode(ctx.getRoot(), 0));
        }

        addChildrenOfPreviousTargetedNode();

        WeightedNode currentWeightedNode;
        while (!nodesFrontierQueue.isEmpty()) {
            currentWeightedNode = nodesFrontierQueue.poll();
            Node currentNode = currentWeightedNode.getNode();
            DecisionData dec = currentNode.decisionData();

            // ----- DECISION NODE -----
            if (dec != null) {
                addChildren(dec);
            }

            // ----- LEAF / VIRGIN NODE -----
            if (currentNode.getDataType() == NodeType.VIRGIN) {
                ctx.constructExpectedPath(currentNode);
                Valuation val = ctx.solvePathOrMarkNode(currentNode);

                if (val != null) {
                    previousTargetedNode = currentNode;
                    return val;
                }
            }

            // Else already a solved node, continue
        }

        return ctx.getPresetValues();
    }
}
