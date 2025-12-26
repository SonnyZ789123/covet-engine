package gov.nasa.jpf.jdart.constraints;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import gov.nasa.jpf.util.JPFLogger;

public class DFSExplorationStrategy implements ExplorationStrategy {

    private final JPFLogger logger = JPF.getLogger("jdart");

    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    private InternalConstraintsTree.Node backtrack(
            InternalConstraintsTree ctx,
            InternalConstraintsTree.Node node,
            boolean pop) {
        if(node == null)
            return null;

        while(!node.isOpen()) {
            boolean exh = node.isExhausted();
            node = node.getParent();

            if (node == null) {
                debugLogger.finest("[backtrack] reached root parent -> stop");
                break;
            }

            if(pop) {
                ctx.solverCtx.pop();
                int removed = ctx.expectedPath.remove(ctx.expectedPath.size() - 1);
                debugLogger.finest(
                        "[backtrack] pop -> removed branch " + removed +
                                ", new expectedPath=" + ctx.expectedPath);
            }

            InternalConstraintsTree.DecisionData dec = node.decisionData();
            dec.decrementOpen();

            if (exh) {
                dec.decrementUnexhausted();
                debugLogger.finest("[backtrack] exhausted child -> decrement unexhausted");
            }
        }

        debugLogger.finest("[backtrack] new expectedPath=" + ctx.expectedPath);
        return node;
    }

    @Override
    public Valuation findNext(InternalConstraintsTree ctx) {
        ctx.replay = false;
        debugLogger.finest("[findNext] entry -> expectedPath=" + ctx.expectedPath);

        if (ctx.diverged) {
            debugLogger.finest("[findNext] divergence detected -> backtrack without pop");
            backtrack(ctx, ctx.current, false);
            ctx.diverged = false;
        }

        ctx.current = ctx.root;

        while ((ctx.currentTarget = backtrack(ctx, ctx.currentTarget, true)) != null) {

            InternalConstraintsTree.DecisionData dec = ctx.currentTarget.decisionData();

            // ----- LEAF / VIRGIN NODE -----
            if (dec == null) {
                assert ctx.currentTarget.isVirgin();

                int ad = ctx.currentTarget.incAltDepth();
                if (ctx.anaConf.maxAltDepthExceeded(ad) || ctx.anaConf.maxDepthExceeded(ctx.currentTarget.getDepth())) {
                    debugLogger.finest(
                            "[findNext] depth limit exceeded -> dontKnow, depth=" +
                                    ctx.currentTarget.getDepth() + ", altDepth=" + ad
                    );
                    ctx.currentTarget.dontKnow();
                    continue;
                }

                Valuation val = new Valuation();
                logger.finer("Finding new valuation");
                debugLogger.finest(
                        "[findNext] solve for " + val +
                                ", expectedPath=" + ctx.expectedPath
                );
                ConstraintSolver.Result res = ctx.solverCtx.solve(val);
                logger.finer("Found: " + res + " : " + val);

                switch (res) {
                    case UNSAT:
                        ctx.currentTarget.unsatisfiable();
                        debugLogger.finest("[findNext] solve -> UNSAT");
                        break;

                    case DONT_KNOW:
                        ctx.currentTarget.dontKnow();
                        debugLogger.finest("[findNext] solve -> DONT_KNOW");
                        break;

                    case SAT:
                        InternalConstraintsTree.Node predictedTarget = ctx.simulate(val);
                        if (predictedTarget != null && predictedTarget != ctx.currentTarget) {
                            boolean inconclusive = predictedTarget.isExhausted();
                            logger.info("Predicted ", inconclusive ? "inconclusive " : "", "divergence");
                            debugLogger.finest("[findNext] predicted divergence -> exhausted=" + inconclusive);
                            if (inconclusive) {
                                debugLogger.finest("[findNext] predicted divergence -> DONT_KNOW");
                                logger.finer("NOT attempting execution");
                                ctx.currentTarget.dontKnow();
                                break;
                            }
                        }

                        if (val.equals(ctx.prev)) {
                            debugLogger.finest("[findNext] duplicate valuation -> skip");
                            logger.finer("Wont re-execute with known valuation");
                            ctx.currentTarget.dontKnow();
                            break;
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
                assert nextIdx != -1;

                Expression<Boolean> constraint = dec.getConstraint(nextIdx);
                InternalConstraintsTree.Node c = dec.getChild(nextIdx);
                ctx.currentTarget = c;

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

        // ----- PRESET FALLBACK -----
        //We fall back on the preset values that might be specified in the
        //jpf config -- this only happens when we cannot find a new target
        //node from exercising the constraints tree
        if (ctx.preset != null && ctx.preset.hasNext()) {
            ctx.current = ctx.root;
            ctx.currentTarget = ctx.root;
            assert ctx.expectedPath.isEmpty();
            ctx.replay = true;

            debugLogger.finest("[findNext] fallback to preset valuation");
            return ctx.preset.next();
        }

        debugLogger.finest("[findNext] no more valuations -> return null");
        return null;
    }
}
