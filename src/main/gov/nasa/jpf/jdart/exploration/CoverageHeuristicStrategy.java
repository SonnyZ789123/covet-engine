package gov.nasa.jpf.jdart.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.constraints.coverage.pathcov.MethodInstructionCoverage;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.jdart.constraints.tree.NodeType;
import gov.nasa.jpf.util.JPFLogger;
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

    private final Queue<Node> currentNodes;
    private Node previousTargetedNode;

    public CoverageHeuristicStrategy() {
        currentNodes = new ArrayDeque<>();
        previousTargetedNode = null;
    }

    private void addChildren(DecisionData decisionData) {
        for (int i = 0; i < decisionData.getBranchWidth(); i++) {
            Node childNode = decisionData.getOrCreateChild(i);
            currentNodes.add(childNode);
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
            currentNodes.add(ctx.getRoot());
        }

        addChildrenOfPreviousTargetedNode();

        Node currentNode;
        while (!currentNodes.isEmpty()) {
            currentNode = currentNodes.poll();
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
