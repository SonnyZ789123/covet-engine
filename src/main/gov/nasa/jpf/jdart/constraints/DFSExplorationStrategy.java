package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;

public class DFSExplorationStrategy implements ExplorationStrategy {

    private final JPFLogger logger = JPF.getLogger("jdart");

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    @Override
    public Valuation findNext(InternalConstraintsTree ctx) {
        debugLogger.finest("[findNext] entry -> expectedPath=" + ctx.expectedPath);

        ctx.findNextInit();

        Node targetNode = ctx.currentTarget;
        while ((targetNode = ctx.backtrack(targetNode, true)) != null) {

            DecisionData dec = targetNode.decisionData();

            // ----- LEAF / VIRGIN NODE -----
            if (dec == null) {
                assert targetNode.isVirgin();

                ctx.checkDepthLimit(targetNode);

                Valuation val = new Valuation();
                logger.finer("Finding new valuation");
                debugLogger.finest(
                        "[findNext] solve for " + val +
                                ", expectedPath=" + ctx.expectedPath
                );
                ConstraintSolver.Result res = ctx.solverCtx.solve(val);
                logger.finer("Found: " + res + " : " + val);

                if (val.equals(ctx.prev)) {
                    debugLogger.finest("[findNext] duplicate valuation -> skip");
                    logger.finer("Wont re-execute with known valuation");
                    targetNode.markDontKnowNode();
                    break;
                }

                switch (res) {
                    case UNSAT:
                        targetNode.markUnsatisfiableNode();
                        debugLogger.finest("[findNext] solve -> UNSAT");
                        break;

                    case DONT_KNOW:
                        targetNode.markDontKnowNode();
                        debugLogger.finest("[findNext] solve -> DONT_KNOW");
                        break;

                    case SAT:
                        Node predictedTarget = ctx.simulate(val);
                        if (predictedTarget != null && predictedTarget != targetNode) {
                            boolean inconclusive = predictedTarget.isExhausted();
                            logger.info("Predicted ", inconclusive ? "inconclusive " : "", "divergence");
                            debugLogger.finest("[findNext] predicted divergence -> exhausted=" + inconclusive);
                            if (inconclusive) {
                                debugLogger.finest("[findNext] predicted divergence -> DONT_KNOW");
                                logger.finer("NOT attempting execution");
                                targetNode.markDontKnowNode();
                                break;
                            }
                        }

                        ctx.prev = val;
                        debugLogger.finest("[findNext] SAT -> returning valuation " + val +
                                " , expectedPath=" + ctx.expectedPath);

                        return ExpressionUtil.combineValuations(val);
                }
            }

            // ----- DECISION NODE -----
            else {
                int nextIdx = dec.nextOpenChild();
                assert nextIdx != -1; // because of backtrack condition

                Expression<Boolean> constraint = dec.getConstraint(nextIdx);
                targetNode = dec.getOrCreateChild(nextIdx);

                ctx.solverCtx.push();
                ctx.expectedPath.add(nextIdx);

                debugLogger.finest(
                        "[findNext] decision node descend -> branch " + nextIdx +
                                ", constraint=" + constraint.toString() +
                                ", new expectedPath=" + ctx.expectedPath
                );

                try {
                    ctx.solverCtx.add(constraint);
                } catch(Exception ex) {
                    logger.finer(ex.getMessage());
                    // ex.printStackTrace();
                    //currentTarget.dontKnow(); // TODO good idea?
                }
            }
        }

        debugLogger.finest("[findNext] fallback to preset valuation");

        return ctx.getPresetValues();
    }
}
