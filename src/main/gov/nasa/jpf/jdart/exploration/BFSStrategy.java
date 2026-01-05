package gov.nasa.jpf.jdart.exploration;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.jdart.constraints.tree.NodeType;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.MethodInfo;

import java.util.*;

public class BFSStrategy implements ExplorationStrategy {
    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    private final Queue<Node> currentNodes;
    private Node previousTargetedNode;

    public BFSStrategy() {
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
