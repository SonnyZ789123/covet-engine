package gov.nasa.jpf.jdart.constraints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.coverage.graph.CoverageEdge;
import gov.nasa.jpf.jdart.constraints.coverage.graph.CoverageGraph;
import gov.nasa.jpf.jdart.constraints.coverage.graph.CoverageNode;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;

import java.io.FileReader;
import java.io.Reader;

public class CoverageHeuristic implements ExplorationStrategy {
    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    private static final CoverageGraph<CoverageNode, CoverageEdge> graph;

    static {
        try {
            // Adjust path as needed (absolute or relative to working dir)
            Reader reader = new FileReader("/workspace/data/jdart_instruction_paths.json");

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            graph = gson.fromJson(reader, CoverageGraph.class);

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public Valuation findNext(InternalConstraintsTree ctx) {
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
//                targetNode = descendDecisionNode(ctx, decisionData);
                // TODO: implement descendDecisionNode
            }
        }

        debugLogger.finest("[findNext] fallback to preset valuation");

        return ctx.getPresetValues();
    }
}
