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

package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.blockmap.model.BlockCoverageDataDTO;
import com.kuleuven.blockmap.model.BlockDataDTO;
import com.kuleuven.blockmap.model.BlockMapDTO;
import com.kuleuven.blockmap.model.BranchType;
import com.kuleuven.blockmap.model.EdgeCoverageDTO;
import com.kuleuven.blockmap.model.MethodBlockMapDTO;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.constraints.tree.InstructionBranch;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.JvmMethodNameConverter;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;

import gov.nasa.jpf.jdart.constraints.tree.DecisionData;

import java.util.*;

/**
 * Tracks CFG edge-level coverage at runtime during concolic execution.
 * Built from the initial block map (from pathcov), and updated as JDart explores new paths.
 *
 * An "edge" is a pair (fromBlockId, toBlockId) representing a branch taken in the CFG.
 * Branch coverage = all edges of all blocks are covered.
 */
public class CfgCoverageTracker {
    private final JPFLogger logger = JPF.getLogger("jdart.debug");

    private final Map<Integer, CfgBlockState> blocks = new HashMap<>();

    /**
     * Per-method lookup: method full name (dotted) -> (line number -> sorted list of branch block IDs).
     * Only blocks with conditional edges (IF_TRUE/IF_FALSE/SWITCH) are included.
     * For lines with a single branch block, the list has one element.
     * For compound conditions (e.g. "if (a || b)"), the list has multiple elements
     * sorted by block ID (topological/bytecode order).
     */
    private final Map<String, Map<Integer, List<Integer>>> methodLineToBranchBlockIds = new HashMap<>();

    /**
     * Cache: maps each encountered Instruction to its resolved block ID.
     * Uses identity equality (same Instruction object = same bytecode instruction).
     */
    private final IdentityHashMap<Instruction, Integer> instructionBlockIdCache = new IdentityHashMap<>();

    /**
     * For lines with multiple branch blocks (compound conditions), tracks
     * bytecode positions of instructions encountered at that line.
     * Used to assign positions to blocks in order: lowest position -> lowest block ID.
     *
     * Structure: method -> line -> TreeMap(position -> instruction)
     */
    private final Map<String, Map<Integer, TreeMap<Integer, Instruction>>> linePositionTrackers = new HashMap<>();

    /** Set of block IDs that have conditional branch edges (IF or SWITCH). */
    private final Set<Integer> conditionalBranchBlockIds = new HashSet<>();

    /**
     * Total branches as defined by pathcov: sum of {@code line.branches.total}
     * across every line of every block of every method in the block map.
     * This is the denominator of the branch-coverage percentage and matches
     * pathcov's {@code BranchCoverage.calculate(BlockMapDTO)}.
     */
    private int pathcovTotalBranches = 0;

    /**
     * Initial covered branches as reported by pathcov (sum of
     * {@code line.branches.covered}) - i.e. branches hit by the existing test
     * suite before JDart starts.
     */
    private int pathcovInitialCovered = 0;

    /**
     * Conditional branches (IF_TRUE/IF_FALSE/SWITCH_*) that pathcov reports as
     * uncovered initially. Encoded as {@code (long) blockId << 32 | branchIndex}.
     * When JDart covers any of these at runtime they bump the covered count.
     */
    private final Set<Long> initiallyUncoveredConditionalBranches = new HashSet<>();

    /**
     * For each conditional branch block, the (method, source line) where the
     * branching bytecode lives. pathcov sums {@code line.branches} per
     * (block, line entry), so the same line can contribute multiple times to
     * the totals; the multiplicity below records how many times.
     */
    private final Map<Integer, String> blockBranchSourceMethod = new HashMap<>();
    private final Map<Integer, Integer> blockBranchSourceLine = new HashMap<>();
    private final Map<String, Integer> lineMultiplicity = new HashMap<>();

    private static String lineKey(String method, int line) {
        return method + "#" + line;
    }

    public CfgCoverageTracker(BlockMapDTO blockMap) {
        // First pass: create all block states and identify branch blocks
        for (MethodBlockMapDTO methodBlockMap : blockMap.methodBlockMaps) {
            String methodName = JvmMethodNameConverter.toDottedClassName(methodBlockMap.fullName);
            // Collect all blocks per line, then filter to branch blocks
            Map<Integer, List<Integer>> lineToAllBlockIds = new HashMap<>();

            for (BlockDataDTO block : methodBlockMap.blocks) {
                Set<Integer> successorIds = new HashSet<>();
                if (block.successorBlockIds != null) {
                    successorIds.addAll(block.successorBlockIds);
                }

                boolean initiallyCovered =
                        block.coverageData.coverageState == BlockCoverageDataDTO.CoverageState.COVERED;

                int totalBranches = (block.edges != null) ? block.edges.size() : 0;
                CfgBlockState state = new CfgBlockState(block.id, successorIds, initiallyCovered, totalBranches);

                // Check if this block has conditional branch edges
                boolean hasConditionalEdges = false;
                if (block.edges != null && !block.edges.isEmpty()) {
                    for (EdgeCoverageDTO edge : block.edges) {
                        boolean conditional =
                                edge.branchType == BranchType.IF_TRUE
                                || edge.branchType == BranchType.IF_FALSE
                                || edge.branchType == BranchType.SWITCH_CASE
                                || edge.branchType == BranchType.SWITCH_DEFAULT;
                        if (conditional) {
                            hasConditionalEdges = true;
                        }
                        if (edge.hits > 0) {
                            state.coveredEdges.add(edge.targetBlockId);
                            int jdartIndex = toJdartBranchIndex(edge);
                            state.initiallyCoveredBranches.add(jdartIndex);
                            state.runtimeCoveredBranches.add(jdartIndex);
                        } else if (conditional) {
                            initiallyUncoveredConditionalBranches.add(
                                    encodeBlockBranch(block.id, toJdartBranchIndex(edge)));
                        }
                    }
                } else {
                    if (initiallyCovered) {
                        state.coveredEdges.addAll(successorIds);
                    }
                }

                // Aggregate pathcov-style branch totals from line.branches summary.
                // pathcov sums per (block, line entry), so the same line in
                // multiple blocks (or repeated within a block) is counted that
                // many times - match it exactly here.
                int branchSourceLine = -1;
                if (block.coverageData.lines != null) {
                    for (com.kuleuven.coverage.model.LineDTO line : block.coverageData.lines) {
                        if (line.branches != null) {
                            pathcovTotalBranches += line.branches.total;
                            pathcovInitialCovered += line.branches.covered;
                            String key = lineKey(methodName, line.line);
                            lineMultiplicity.merge(key, 1, Integer::sum);
                            if (line.branches.total > 0 && branchSourceLine == -1) {
                                branchSourceLine = line.line;
                            }
                        }
                    }
                }

                if (hasConditionalEdges) {
                    conditionalBranchBlockIds.add(block.id);
                    if (branchSourceLine != -1) {
                        blockBranchSourceMethod.put(block.id, methodName);
                        blockBranchSourceLine.put(block.id, branchSourceLine);
                    }
                }

                blocks.put(block.id, state);

                // Build line -> list of block IDs mapping
                if (block.coverageData.lines != null) {
                    for (com.kuleuven.coverage.model.LineDTO line : block.coverageData.lines) {
                        lineToAllBlockIds.computeIfAbsent(line.line, k -> new ArrayList<>()).add(block.id);
                    }
                }
            }

            // Build the branch block mapping: for each line, keep only blocks with conditional edges
            Map<Integer, List<Integer>> lineToBranchBlocks = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> entry : lineToAllBlockIds.entrySet()) {
                int line = entry.getKey();
                // Deduplicate block IDs (a block can appear multiple times if it has multiple lines)
                Set<Integer> uniqueIds = new LinkedHashSet<>(entry.getValue());
                // Filter to only conditional branch blocks
                List<Integer> branchBlocks = new ArrayList<>();
                for (int blockId : uniqueIds) {
                    if (conditionalBranchBlockIds.contains(blockId)) {
                        branchBlocks.add(blockId);
                    }
                }
                // Sort by block ID (= topological order in the CFG)
                Collections.sort(branchBlocks);
                if (!branchBlocks.isEmpty()) {
                    lineToBranchBlocks.put(line, branchBlocks);
                }
            }

            methodLineToBranchBlockIds.put(methodName, lineToBranchBlocks);
        }
    }

    /**
     * Get the block ID for a given branch instruction, or -1 if not found.
     *
     * Uses a multi-level resolution strategy to correctly handle lines with
     * multiple CFG blocks (e.g. compound conditions, if-body blocks):
     * 1. Check the instruction identity cache first.
     * 2. For lines with a single branch block, return it directly.
     * 3. For lines with multiple branch blocks (compound conditions), use
     *    bytecode position ordering: lower position -> lower block ID.
     */
    public int getBlockIdForInstruction(Instruction instruction) {
        if (instruction == null) {
            return -1;
        }

        // Check cache
        Integer cached = instructionBlockIdCache.get(instruction);
        if (cached != null) {
            return cached;
        }

        MethodInfo mi = instruction.getMethodInfo();
        String methodName = mi.getFullName();
        Map<Integer, List<Integer>> lineToBranchBlocks = methodLineToBranchBlockIds.get(methodName);
        if (lineToBranchBlocks == null) {
            return -1;
        }

        int line = instruction.getLineNumber();
        List<Integer> branchBlocks = lineToBranchBlocks.get(line);
        if (branchBlocks == null || branchBlocks.isEmpty()) {
            return -1;
        }

        if (branchBlocks.size() == 1) {
            int blockId = branchBlocks.get(0);
            instructionBlockIdCache.put(instruction, blockId);
            return blockId;
        }

        // Multiple branch blocks at same line (compound condition).
        // Use bytecode position to disambiguate.
        return resolveMultiBlockLine(instruction, methodName, line, branchBlocks);
    }

    /**
     * Resolve block ID for an instruction at a line with multiple branch blocks.
     * Uses the position tracker (populated by registerInstructionPosition in
     * extractCfgEdges) to disambiguate: lowest registered position -> lowest block ID.
     *
     * If the instruction's position was NOT registered (e.g. it's a target instruction
     * from addChildren, not a branch instruction from extractCfgEdges), returns -1.
     * This prevents non-branch instructions from polluting the position-to-block mapping.
     */
    private int resolveMultiBlockLine(Instruction instruction, String methodName, int line,
                                      List<Integer> branchBlocks) {
        int position = instruction.getPosition();

        // Only resolve using positions that were pre-registered by extractCfgEdges.
        // Do NOT add new positions here (addChildren target instructions would corrupt the mapping).
        Map<Integer, TreeMap<Integer, Instruction>> methodTrackers = linePositionTrackers.get(methodName);
        if (methodTrackers == null) {
            return -1;
        }
        TreeMap<Integer, Instruction> posMap = methodTrackers.get(line);
        if (posMap == null || !posMap.containsKey(position)) {
            return -1;
        }

        // Assign registered positions to blocks: nth position -> nth block
        int idx = 0;
        for (Map.Entry<Integer, Instruction> entry : posMap.entrySet()) {
            int blockId = (idx < branchBlocks.size()) ? branchBlocks.get(idx) : branchBlocks.get(branchBlocks.size() - 1);
            instructionBlockIdCache.put(entry.getValue(), blockId);
            idx++;
        }

        return instructionBlockIdCache.get(instruction);
    }

    /**
     * Extract the branch decisions taken along an execution path.
     * Returns a list of (fromBlockId, branchIndex) pairs representing the
     * specific branches taken at each decision point.
     *
     * Uses a two-pass approach to correctly handle lines with multiple blocks:
     * 1. First pass (bottom-up): collect all branch instructions and their indices.
     * 2. Register all instruction positions for multi-block lines.
     * 3. Second pass: resolve block IDs using position ordering.
     *
     * This ensures that for compound conditions (e.g. "if (a || b)"), all bytecode
     * positions at the same line are known before assignment, so the nth position
     * correctly maps to the nth block.
     */
    public List<int[]> extractCfgEdges(Node leafNode) {
        // First pass: collect (instruction, branchIndex) pairs
        List<Object[]> rawEdges = new ArrayList<>(); // [Instruction, int branchIndex]
        Node current = leafNode;

        while (current != null) {
            Node parent = current.getParent();
            if (parent == null) {
                break;
            }

            DecisionData parentDec = parent.decisionData();
            if (parentDec != null) {
                Instruction branchInsn = parentDec.getBranchInstruction();
                if (branchInsn != null) {
                    int branchIndex = findChildIndex(parentDec, current);
                    if (branchIndex != -1) {
                        rawEdges.add(new Object[]{branchInsn, branchIndex});
                    }
                }
            }

            current = parent;
        }

        // Register all instruction positions before resolving.
        // This ensures compound conditions at the same line have all positions
        // known, so position->block assignment is correct.
        for (Object[] raw : rawEdges) {
            Instruction insn = (Instruction) raw[0];
            // Trigger position registration without caching yet
            registerInstructionPosition(insn);
        }

        // Second pass: resolve block IDs
        List<int[]> branchDecisions = new ArrayList<>();
        for (Object[] raw : rawEdges) {
            Instruction insn = (Instruction) raw[0];
            int branchIndex = (int) raw[1];
            int fromBlockId = getBlockIdForInstruction(insn);
            if (fromBlockId != -1) {
                branchDecisions.add(new int[]{fromBlockId, branchIndex});
            }
        }

        return branchDecisions;
    }

    /**
     * Register an instruction's bytecode position for multi-block line resolution.
     * This does NOT resolve the block ID; it only records the position so that
     * subsequent resolution considers all known positions at the line.
     */
    private void registerInstructionPosition(Instruction instruction) {
        if (instruction == null) return;

        MethodInfo mi = instruction.getMethodInfo();
        String methodName = mi.getFullName();
        Map<Integer, List<Integer>> lineToBranchBlocks = methodLineToBranchBlockIds.get(methodName);
        if (lineToBranchBlocks == null) return;

        int line = instruction.getLineNumber();
        List<Integer> branchBlocks = lineToBranchBlocks.get(line);
        if (branchBlocks == null || branchBlocks.size() <= 1) return;

        // Only need to register for multi-block lines
        int position = instruction.getPosition();
        Map<Integer, TreeMap<Integer, Instruction>> methodTrackers =
                linePositionTrackers.computeIfAbsent(methodName, k -> new HashMap<>());
        TreeMap<Integer, Instruction> posMap =
                methodTrackers.computeIfAbsent(line, k -> new TreeMap<>());
        posMap.put(position, instruction);
    }

    /**
     * Convert a block map edge's branchIndex to JDart's child index.
     *
     * Despite the IF_TRUE/IF_FALSE naming, the block map's branchIndex already uses
     * bytecode convention: branchIndex=0 (IF_TRUE) = bytecode jump taken, branchIndex=1
     * (IF_FALSE) = bytecode fall-through. This matches JDart's convention where child 0 =
     * jump taken and child 1 = fall-through. No conversion needed.
     *
     * The IF_TRUE label means "the Jimple/bytecode jump CONDITION is true" (not "the source
     * if-condition is true"). See EdgeCoverage analysis docs and pathcov CHANGELOG.
     */
    private static int toJdartBranchIndex(EdgeCoverageDTO edge) {
        return edge.branchIndex;
    }

    private int findChildIndex(DecisionData parentDec, Node child) {
        Node[] children = parentDec.getChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] == child) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Record branch decisions for exploration guiding (runtime tracking).
     * Updates runtimeCoveredBranches but NOT initiallyCoveredBranches.
     */
    public void recordPathEdges(List<int[]> branchDecisions) {
        for (int[] decision : branchDecisions) {
            int fromId = decision[0];
            int branchIndex = decision[1];
            CfgBlockState block = blocks.get(fromId);
            if (block != null) {
                block.runtimeCoveredBranches.add(branchIndex);
            }
        }
    }

    /**
     * Record the edges of a completed execution path (convenience method).
     * Also marks all blocks along the path as visited.
     */
    public void recordCompletedPath(Node leafNode) {
        List<int[]> edges = extractCfgEdges(leafNode);
        recordPathEdges(edges);
        markBlocksVisited(leafNode);
    }

    /**
     * Mark all blocks along the execution path as visited.
     */
    private void markBlocksVisited(Node leafNode) {
        Node current = leafNode;
        while (current != null) {
            InstructionBranch ib = current.getInstructionBranch();
            if (ib != null && ib.getInstruction() != null) {
                int blockId = getBlockIdForInstruction(ib.getInstruction());
                if (blockId != -1) {
                    CfgBlockState block = blocks.get(blockId);
                    if (block != null) {
                        block.visited = true;
                    }
                }
            }
            current = current.getParent();
        }
    }

    /**
     * Check if all branch decisions in the path are covered at RUNTIME
     * (initial test suite + JDart discoveries). A path is redundant when
     * every branch it takes has already been explored.
     */
    public boolean areAllEdgesCovered(List<int[]> branchDecisions) {
        if (branchDecisions.isEmpty()) {
            return false;
        }

        for (int[] decision : branchDecisions) {
            int fromId = decision[0];
            int branchIndex = decision[1];
            CfgBlockState block = blocks.get(fromId);
            if (block == null || !block.runtimeCoveredBranches.contains(branchIndex)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the weight for a target block. Used by the priority queue.
     * Returns 0 if the block has any uncovered branch at runtime (high priority).
     * Returns 1 if all branches are covered at runtime (low priority).
     *
     * Uses RUNTIME tracking (initial + JDart discoveries). Once all branches
     * of a block are explored, the block gets weight 1. This guides the explorer
     * toward blocks that still have uncovered branches and allows IGNORE detection
     * for paths that traverse only fully-covered blocks.
     */
    public double getWeight(int blockId) {
        CfgBlockState block = blocks.get(blockId);
        if (block == null) {
            return 0;
        }
        return block.isFullyCovered() ? 1 : 0;
    }

    /**
     * Effective branch coverage as a percentage in [0, 100]. Uses the same
     * denominator and initial numerator as pathcov's
     * {@code BranchCoverage.calculate(BlockMapDTO)} (sum of
     * {@code line.branches.total} and {@code line.branches.covered}), then
     * adds any conditional branches JDart has covered at runtime that were
     * uncovered initially. Returns 100 when there are no branches.
     */
    public double getBranchCoveragePercentage() {
        int newlyCovered = 0;
        for (Long key : initiallyUncoveredConditionalBranches) {
            int blockId = (int) (key >> 32);
            int branchIndex = key.intValue();
            CfgBlockState b = blocks.get(blockId);
            if (b == null || !b.runtimeCoveredBranches.contains(branchIndex)) continue;
            String method = blockBranchSourceMethod.get(blockId);
            Integer line = blockBranchSourceLine.get(blockId);
            int mult = (method != null && line != null)
                    ? lineMultiplicity.getOrDefault(lineKey(method, line), 1)
                    : 1;
            newlyCovered += mult;
        }
        int covered = Math.min(pathcovInitialCovered + newlyCovered, pathcovTotalBranches);
        return pathcovTotalBranches == 0 ? 100.0 : (100.0 * covered) / pathcovTotalBranches;
    }

    private static long encodeBlockBranch(int blockId, int branchIndex) {
        return ((long) blockId << 32) | (branchIndex & 0xFFFFFFFFL);
    }

    /**
     * Get the weight for a specific branch of a block (for exploration guiding).
     * Uses RUNTIME coverage (initial + JDart discoveries).
     * Returns 0 if the branch is NOT covered (high priority).
     * Returns 1 if the branch IS covered (low priority).
     */
    public double getBranchWeight(int fromBlockId, int branchIndex) {
        if (fromBlockId == -1) {
            return 0;
        }
        CfgBlockState fromBlock = blocks.get(fromBlockId);
        if (fromBlock == null) {
            return 0;
        }
        return fromBlock.runtimeCoveredBranches.contains(branchIndex) ? 1 : 0;
    }

    /**
     * Internal state for a single CFG block.
     */
    static class CfgBlockState {
        final int id;
        final Set<Integer> successorIds;
        final Set<Integer> coveredEdges;
        /** Branch indices initially covered by the existing test suite. Used for IGNORE detection. */
        final Set<Integer> initiallyCoveredBranches;
        /** Branch indices covered at runtime (initial + JDart discoveries). Used for exploration guiding. */
        final Set<Integer> runtimeCoveredBranches;
        /** Total number of outgoing branches (from edge data). */
        final int totalBranches;
        final boolean initiallyCovered;
        boolean visited;

        CfgBlockState(int id, Set<Integer> successorIds, boolean initiallyCovered, int totalBranches) {
            this.id = id;
            this.successorIds = successorIds;
            this.coveredEdges = new HashSet<>();
            this.initiallyCoveredBranches = new HashSet<>();
            this.runtimeCoveredBranches = new HashSet<>();
            this.totalBranches = totalBranches;
            this.initiallyCovered = initiallyCovered;
            this.visited = initiallyCovered;
        }

        /**
         * A block is fully covered (for exploration guiding) if all its outgoing branches
         * have been covered (either initially or by JDart runtime discoveries).
         *
         * For branch blocks: uses runtime branch tracking (precise).
         * For non-branch/leaf blocks: uses initial coverage only. Different constraint
         * paths to the same leaf block cover different intermediate branches, so we
         * must NOT mark them as covered after the first JDart visit.
         */
        boolean isFullyCovered() {
            if (totalBranches > 0) {
                return runtimeCoveredBranches.size() >= totalBranches;
            }
            return initiallyCovered;
        }
    }
}
