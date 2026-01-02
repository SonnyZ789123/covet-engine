package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.MethodInfo;

public class DFSExplorationStrategy implements ExplorationStrategy {

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    private Node descendDecisionNode(InternalConstraintsTree ctx, DecisionData decisionData) {
        int nextIdx = decisionData.nextOpenChild();
        assert nextIdx != -1; // because of backtrack condition should decisionData have at least one open child

        ctx.extendExpectedPath(decisionData, nextIdx);
        return decisionData.getOrCreateChild(nextIdx);
    }

    @Override
    public Valuation findNext(InternalConstraintsTree ctx, MethodInfo methodInfo) {
        debugLogger.finest("[findNext] entry -> expectedPath=" + ctx.expectedPath);

        ctx.findNextInit();

        Node targetNode = ctx.currentTarget;
        while ((targetNode = ctx.backtrack(targetNode, false, Node::isOpen)) != null) {

            DecisionData decisionData = targetNode.decisionData();

            // ----- LEAF / VIRGIN NODE -----
            if (decisionData == null) {
                Valuation val = ctx.solvePathOrMarkNode(targetNode);

                if (val != null) {
                    return val;
                }
            }

            // ----- DECISION NODE -----
            else {
                targetNode = descendDecisionNode(ctx, decisionData);
            }
        }

        debugLogger.finest("[findNext] fallback to preset valuation");

        return ctx.getPresetValues();
    }
}
