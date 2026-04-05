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

    // Per-method lookup: method full name (dotted) -> (line number -> block ID)
    private final Map<String, Map<Integer, Integer>> methodLineToBlockId = new HashMap<>();

    public CfgCoverageTracker(BlockMapDTO blockMap) {
        // First pass: create all block states
        for (MethodBlockMapDTO methodBlockMap : blockMap.methodBlockMaps) {
            String methodName = JvmMethodNameConverter.toDottedClassName(methodBlockMap.fullName);
            Map<Integer, Integer> lineToBlockId = new HashMap<>();

            for (BlockDataDTO block : methodBlockMap.blocks) {
                Set<Integer> successorIds = new HashSet<>();
                if (block.successorBlockIds != null) {
                    successorIds.addAll(block.successorBlockIds);
                }

                boolean initiallyCovered =
                        block.coverageData.coverageState == BlockCoverageDataDTO.CoverageState.COVERED;

                int totalBranches = (block.edges != null) ? block.edges.size() : 0;
                CfgBlockState state = new CfgBlockState(block.id, successorIds, initiallyCovered, totalBranches);

                // Use edge-level data when available (v3.1.0+)
                if (block.edges != null && !block.edges.isEmpty()) {
                    for (EdgeCoverageDTO edge : block.edges) {
                        if (edge.hits > 0) {
                            state.coveredEdges.add(edge.targetBlockId);
                            // Convert block map's source-level branchIndex to JDart's bytecode-level index.
                            // Block map: branchIndex 0 = IF_TRUE (source true), 1 = IF_FALSE (source false)
                            // JDart:     child 0 = bytecode jump taken (= source false), 1 = fall-through (= source true)
                            // So for IF branches: invert the index. For SWITCH: no inversion needed.
                            int jdartIndex = toJdartBranchIndex(edge);
                            state.initiallyCoveredBranches.add(jdartIndex);
                            state.runtimeCoveredBranches.add(jdartIndex);
                        }
                    }
                } else {
                    // Fallback: old behavior for backward compatibility with data
                    // that doesn't include edge information
                    if (initiallyCovered) {
                        state.coveredEdges.addAll(successorIds);
                    }
                }

                blocks.put(block.id, state);

                // Build line -> blockId mapping
                if (block.coverageData.lines != null) {
                    for (com.kuleuven.coverage.model.LineDTO line : block.coverageData.lines) {
                        lineToBlockId.put(line.line, block.id);
                    }
                }
            }

            methodLineToBlockId.put(methodName, lineToBlockId);
        }
    }

    /**
     * Get the block ID for a given instruction, or -1 if not found.
     */
    public int getBlockIdForInstruction(Instruction instruction) {
        if (instruction == null) {
            return -1;
        }
        MethodInfo mi = instruction.getMethodInfo();
        Map<Integer, Integer> lineToBlock = methodLineToBlockId.get(mi.getFullName());
        if (lineToBlock == null) {
            return -1;
        }
        Integer blockId = lineToBlock.get(instruction.getLineNumber());
        return blockId != null ? blockId : -1;
    }

    /**
     * Extract the list of CFG branch edges taken along an execution path.
     *
     * For each decision node along the path, uses the parent's branch instruction
     * (from-block) and the child's target instruction (to-block) to identify the
     * exact CFG edge taken. This correctly handles interprocedural paths: cross-method
     * transitions are skipped (the block map has no cross-method successor edges),
     * while within-method branch edges are captured precisely.
     */
    /**
     * Extract the branch decisions taken along an execution path.
     * Returns a list of (fromBlockId, branchIndex) pairs representing the
     * specific branches taken at each decision point.
     *
     * Uses the parent decision's branchInsn for the from-block and determines
     * the branch index by finding which child of the parent matches the current node.
     * This avoids the toBlockId line-number ambiguity that caused incorrect edge matching.
     */
    public List<int[]> extractCfgEdges(Node leafNode) {
        List<int[]> branchDecisions = new ArrayList<>();
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
                    int fromBlockId = getBlockIdForInstruction(branchInsn);
                    if (fromBlockId != -1) {
                        int branchIndex = findChildIndex(parentDec, current);
                        if (branchIndex != -1) {
                            branchDecisions.add(new int[]{fromBlockId, branchIndex});
                        }
                    }
                }
            }

            current = parent;
        }

        return branchDecisions;
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
     * Record the branch decisions taken by a completed execution path.
     * Each entry is (fromBlockId, branchIndex). Updates the runtime coverage tracking.
     */
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
     * Check if all edges in the given path have already been covered.
     * Used for duplicate detection: if all edges are covered, the path is redundant.
     */
    /**
     * Check if all branch decisions in the path were INITIALLY covered
     * (by the existing test suite, not by JDart runtime discoveries).
     * This prevents JDart from marking its own discoveries as redundant.
     */
    public boolean areAllEdgesCovered(List<int[]> branchDecisions) {
        if (branchDecisions.isEmpty()) {
            return false;
        }

        for (int[] decision : branchDecisions) {
            int fromId = decision[0];
            int branchIndex = decision[1];
            CfgBlockState block = blocks.get(fromId);
            if (block == null || !block.initiallyCoveredBranches.contains(branchIndex)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the weight for a target block. Used by the priority queue.
     * Returns 0 if the block is not fully covered by the initial test suite (high priority).
     * Returns 1 if initially fully covered (low priority).
     *
     * Deliberately uses STATIC initial coverage, not runtime tracking. A "fully covered"
     * block (all branches explored) can still be on paths to different uncovered code
     * depending on concrete input values. Static weight preserves path diversity through
     * partially-covered blocks, achieving higher overall branch coverage than runtime.
     * Empirically: static 77.21% vs runtime 75.86% on FinScore8.
     */
    public double getWeight(int blockId) {
        CfgBlockState block = blocks.get(blockId);
        if (block == null) {
            return 0;
        }
        return block.initiallyCovered ? 1 : 0;
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
