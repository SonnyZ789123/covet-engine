package gov.nasa.jpf.jdart.constraints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.coverage.pathcov.MethodInstructionCoverage;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.MethodInfo;

import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class CoverageHeuristic implements ExplorationStrategy {
    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

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
//                targetNode = descendDecisionNode(ctx, decisionData);
                // TODO: implement descendDecisionNode
            }
        }

        debugLogger.finest("[findNext] fallback to preset valuation");

        return ctx.getPresetValues();
    }
}
