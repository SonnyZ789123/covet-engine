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
    private final CfgCoverageTracker cfgCoverageTracker;

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
            cfgCoverageTracker = new CfgCoverageTracker(blockMapFromJson);

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
        int blockId = cfgCoverageTracker.getBlockIdForInstruction(instruction);
        return cfgCoverageTracker.getWeight(blockId);
    }

    private String getBlockHash(Instruction instruction) {
        MethodInfo mi = instruction.getMethodInfo();

        MethodBlockMapCoverage cov = coverageCache.computeIfAbsent(mi,
                m -> blockMapCoverage.getMethodBlockMapCoverage(mi.getFullName())
        );

        if (cov == null) {
            return null;
        }

        return cov.getBlockHashForLine(instruction.getLineNumber());
    }

    private void addChildren(DecisionData decisionData) {
        // Determine the "from" block: the block containing the branch instruction
        Instruction branchInsn = decisionData.getBranchInstruction();
        int fromBlockId = cfgCoverageTracker.getBlockIdForInstruction(branchInsn);

        for (int i = 0; i < decisionData.getBranchWidth(); i++) {
            Instruction nextInstruction = decisionData.getNextInstruction(i);
            double weight;

            if (nextInstruction == null) {
                weight = 0;
            } else if (fromBlockId != -1) {
                // Edge-level weight: is this specific branch (from -> to) covered?
                int toBlockId = cfgCoverageTracker.getBlockIdForInstruction(nextInstruction);
                weight = cfgCoverageTracker.getEdgeWeight(fromBlockId, toBlockId);
            } else {
                // Fallback: block-level weight when we can't determine the from-block
                weight = computeWeight(nextInstruction);
            }

            Node childNode = decisionData.getOrCreateChild(i);
            nodesFrontierQueue.add(new WeightedNode(childNode, weight));
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

    /**
     * Record the CFG edges covered by a completed execution path.
     * This updates the runtime coverage tracking so that future paths
     * can be correctly identified as duplicates or not.
     */
    public void recordCompletedPath(Node finalTarget) {
        cfgCoverageTracker.recordCompletedPath(finalTarget);
    }

    public Set<String> getBlockHashesAlongPath(Node finalTarget) {
        Set<String> blockHashes = new HashSet<>();

        Node currentNode = finalTarget;
        while (currentNode != null) {
            InstructionBranch instructionBranch = currentNode.getInstructionBranch();
            if (instructionBranch != null) {
                Instruction insn = instructionBranch.getInstruction();

                if (insn != null) {
                    String blockHash = getBlockHash(insn);

                    if (blockHash != null) {
                        blockHashes.add(blockHash);
                    }
                }
            }

            currentNode = currentNode.getParent();
        }
        return blockHashes;
    }

    /**
     * Check if a path is already covered by examining the initial block map coverage.
     * A path is considered covered if every instruction along the path belongs to a
     * COVERED block in the initial coverage data (from the existing test suite).
     *
     * This uses only STATIC coverage from the initial test suite, not runtime tracking.
     * The block map has only method-local successor edges (no cross-method edges),
     * so edge-based checking via extractCfgEdges produces false positives for
     * interprocedural paths. Block-level checking is conservative and correct:
     * if any instruction is in an uncovered/partially-covered block or in an
     * unmapped method, the path is NOT considered covered.
     */
    public boolean pathIsBlockCovered(Node finalTarget) {
        Node currentNode = finalTarget;
        while (currentNode != null) {
            InstructionBranch instructionBranch = currentNode.getInstructionBranch();
            if (instructionBranch == null) {
                currentNode = currentNode.getParent();
                continue;
            }

            Instruction insn = instructionBranch.getInstruction();
            if (insn == null) {
                return false;
            }

            MethodInfo mi = insn.getMethodInfo();
            MethodBlockMapCoverage cov = coverageCache.computeIfAbsent(mi,
                    m -> blockMapCoverage.getMethodBlockMapCoverage(mi.getFullName())
            );

            if (cov == null) {
                return false;
            }

            BlockCoverageDataDTO.CoverageState state = cov.getCoverageStateForLine(insn.getLineNumber());

            if (state == null) {
                currentNode = currentNode.getParent();
                continue;
            }

            if (state != BlockCoverageDataDTO.CoverageState.COVERED) {
                return false;
            }

            currentNode = currentNode.getParent();
        }

        return true;
    }
}
