/*
 * Copyright (C) 2025-2026 Yoran Mertens
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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

    public CfgCoverageTracker getCoverageTracker() {
        return cfgCoverageTracker;
    }

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

    private int pathCounter = 0;

    private void addChildren(DecisionData decisionData) {
        Instruction branchInsn = decisionData.getBranchInstruction();
        String branchInfo = branchInsn != null
                ? branchInsn.getMethodInfo().getName() + ":" + branchInsn.getLineNumber()
                : "unknown";

        for (int i = 0; i < decisionData.getBranchWidth(); i++) {
            Instruction nextInstruction = decisionData.getNextInstruction(i);
            double weight;

            if (nextInstruction == null) {
                weight = 0;
            } else {
                int targetBlockId = cfgCoverageTracker.getBlockIdForInstruction(nextInstruction);
                weight = cfgCoverageTracker.getWeight(targetBlockId);
                debugLogger.finest("[addChildren] branch " + branchInfo + " child " + i
                        + " -> targetBlock=" + targetBlockId + " weight=" + weight);
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
                    pathCounter++;
                    debugLogger.finest("[findNext] PATH #" + pathCounter + " targeted at depth="
                            + currentNode.getDepth() + " weight=" + currentWeightedNode.getWeight());
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
     * Check if a path is already covered using runtime edge-level tracking.
     * A path is IGNORE if every within-method branch edge along the path has
     * already been covered (either from initial coverage data or previous JDart runs).
     *
     * Uses per-decision edge extraction: for each decision along the path, checks
     * the edge from the branch instruction's block to the taken branch's target block.
     * Cross-method transitions are skipped (the block map has method-local edges only).
     */
    public boolean pathIsBlockCovered(Node finalTarget) {
        List<int[]> edges = cfgCoverageTracker.extractCfgEdges(finalTarget);
        boolean result = cfgCoverageTracker.areAllEdgesCovered(edges);
        debugLogger.finest("[pathIsBlockCovered] edges=" + edges.size() + " -> " + (result ? "IGNORE" : "OK"));
        return result;
    }
}
