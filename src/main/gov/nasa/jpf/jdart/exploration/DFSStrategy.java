package gov.nasa.jpf.jdart.exploration;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.MethodInfo;

public class DFSStrategy implements ExplorationStrategy {

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    public DFSStrategy() {}

    private Node descendDecisionNode(InternalConstraintsTree ctx, DecisionData decisionData) {
        int nextIdx = decisionData.nextOpenChild();
        assert nextIdx != -1; // because of backtrack condition should decisionData have at least one open child

        ctx.extendExpectedPath(decisionData, nextIdx);
        return decisionData.getOrCreateChild(nextIdx);
    }

    @Override
    public Valuation findNext(InternalConstraintsTree ctx, MethodInfo methodInfo) {
        debugLogger.finest("[findNext] ================ finding next path ================");

        ctx.findNextInit();

        Node targetNode = ctx.getCurrentTarget();
        while ((targetNode = ctx.backtrack(targetNode, Node::isOpen)) != null) {

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

        return ctx.getPresetValues();
    }
}
