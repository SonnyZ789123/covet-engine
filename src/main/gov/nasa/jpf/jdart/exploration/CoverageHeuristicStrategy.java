package gov.nasa.jpf.jdart.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuleuven.blockmap.model.BlockCoverageDataDTO;
import com.kuleuven.blockmap.model.BlockMapDTO;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.jdart.constraints.InternalConstraintsTree;
import gov.nasa.jpf.jdart.constraints.PathResult;
import gov.nasa.jpf.jdart.constraints.tree.InstructionBranch;
import gov.nasa.jpf.jdart.exploration.coverage.*;
import gov.nasa.jpf.jdart.constraints.tree.DecisionData;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.jdart.constraints.tree.NodeType;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;

import java.io.FileReader;
import java.io.Reader;
import java.util.*;

public class CoverageHeuristicStrategy implements ExplorationStrategy {
    private final JPFLogger debugLogger = JPF.getLogger("jdart.debug");

    private static final String DEFAULT_CONFIG_FILE = "/jdart-project/data/coverage_heuristic.config";

    public final BlockMapCoverage blockMapCoverage;
    public final boolean shouldIgnoreCoveredPaths;

    private Properties readConfiguration(String configFilePath) {
        Properties props = new Properties();

        try (FileReader reader = new FileReader(configFilePath != null ? configFilePath : DEFAULT_CONFIG_FILE)) {
            props.load(reader);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read configuration file", e);
        }

        return props;
    }

    private final Queue<WeightedNode> nodesFrontierQueue;
    private Node previousTargetedNode;
    private final Map<MethodInfo, MethodBlockMapCoverage> coverageCache = new IdentityHashMap<>();


    public CoverageHeuristicStrategy(String configFilePath) {
        try {
            Properties properties = readConfiguration(configFilePath);
            shouldIgnoreCoveredPaths = Boolean.parseBoolean(
                    properties.getProperty(
                            "jdart.exploration.coverage_heuristic.ignore_covered_paths",
                            "false"));
            String coverageDataPath = properties.getProperty(
                    "jdart.exploration.coverage_heuristic.coverage_data_path",
                    "/data/blockmaps/icfg_block_map.json"
            );

            // Adjust path as needed (absolute or relative to working dir)
            Reader reader = new FileReader(coverageDataPath);

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

            BlockMapDTO blockMapFromJson = gson.fromJson(reader, BlockMapDTO.class);
            blockMapCoverage = new BlockMapCoverage(blockMapFromJson);

            reader.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize CoverageHeuristicStrategy: " + e);
        }

        // Initialize a priority queue: lower weight, and lower depth have higher priority
        nodesFrontierQueue = new PriorityQueue<>(
                Comparator.comparingDouble(WeightedNode::getWeight)
                        .thenComparingInt(w -> w.getNode().getDepth())
        );
        previousTargetedNode = null;
    }

    private double computeWeight(Instruction instruction) {
        MethodInfo mi = instruction.getMethodInfo();

        MethodBlockMapCoverage cov = coverageCache.computeIfAbsent(mi,
                m -> blockMapCoverage.getMethodBlockMapCoverage(mi.getFullName())
        );

        if (cov == null) {
            return 0;
        }

        return cov.getCoverageStateForLine(instruction.getLineNumber()) == BlockCoverageDataDTO.CoverageState.COVERED ? 1 : 0;
    }


    private void addChildren(DecisionData decisionData) {
        for (int i = 0; i < decisionData.getBranchWidth(); i++) {
            Instruction nextInstruction = decisionData.getNextInstruction(i);
            double weight = nextInstruction == null ? 0 : computeWeight(nextInstruction);

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
        debugLogger.finest("[findNext] ================ finding next path ================");

        ctx.findNextInit();

        // Start of the concolic method execution
        if (previousTargetedNode == null) {
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

    public boolean pathIsBlockCovered(Node finalTarget) {
        Node currentNode = finalTarget;
        while (currentNode != null) {
            InstructionBranch instructionBranch = currentNode.getInstructionBranch();
            if (instructionBranch == null) {
                // The root node does not have an associated instruction branch.
                // Some exception branches (e.g., division by zero) might not have an associated instruction.
                currentNode = currentNode.getParent();
                continue;
            }

            Instruction insn = instructionBranch.getInstruction();
            if (insn == null) { // This is possible for branches created by uncaught exceptions (e.g., div by zero)
                System.err.println("insn is null for instruction branch: " + instructionBranch);
                return false;
            }

            MethodInfo mi = insn.getMethodInfo();
            MethodBlockMapCoverage cov = blockMapCoverage.getMethodBlockMapCoverage(mi.getFullName());

            if (cov == null) {
                System.err.println("coverage data not found for method: " + mi.getFullName());
                return false;
            }

            BlockCoverageDataDTO.CoverageState state = cov.getCoverageStateForLine(insn.getLineNumber());

            // The line does not map to any block, is this possible?
            if (state == null) {
                currentNode = currentNode.getParent();
                continue;
            }

            if (state != BlockCoverageDataDTO.CoverageState.COVERED) {
                System.err.println("block not covered for instruction: " + insn + " at line " + insn.getLineNumber() + " in method " + mi.getFullName());
                return false;
            }

            currentNode = currentNode.getParent();
        }

        return true;
    }
}
