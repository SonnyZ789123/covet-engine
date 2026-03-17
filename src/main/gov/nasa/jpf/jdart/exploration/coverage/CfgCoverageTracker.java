package gov.nasa.jpf.jdart.exploration.coverage;

import com.kuleuven.blockmap.model.BlockCoverageDataDTO;
import com.kuleuven.blockmap.model.BlockDataDTO;
import com.kuleuven.blockmap.model.BlockMapDTO;
import com.kuleuven.blockmap.model.MethodBlockMapDTO;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.jdart.constraints.tree.InstructionBranch;
import gov.nasa.jpf.jdart.constraints.tree.Node;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.JvmMethodNameConverter;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.MethodInfo;

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

                CfgBlockState state = new CfgBlockState(block.id, successorIds, initiallyCovered);

                // If initially covered, mark all outgoing edges as covered
                if (initiallyCovered) {
                    state.coveredEdges.addAll(successorIds);
                }
                // PARTIALLY_COVERED and NOT_COVERED: no edges marked (conservative)

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
     * Extract the list of CFG edges (fromBlockId, toBlockId) taken along an execution path.
     * Walks from the leaf node up to the root, then reverses to get root-to-leaf order,
     * and extracts consecutive block pairs that are connected by CFG edges.
     */
    public List<int[]> extractCfgEdges(Node leafNode) {
        // Collect block IDs from leaf to root
        List<Integer> blockIdsReversed = new ArrayList<>();
        Node current = leafNode;
        int prevBlockId = -1;

        while (current != null) {
            InstructionBranch ib = current.getInstructionBranch();
            if (ib != null && ib.getInstruction() != null) {
                int blockId = getBlockIdForInstruction(ib.getInstruction());
                if (blockId != -1 && blockId != prevBlockId) {
                    blockIdsReversed.add(blockId);
                    prevBlockId = blockId;
                }
            }
            current = current.getParent();
        }

        // Reverse to get root-to-leaf order
        List<Integer> blockIds = new ArrayList<>(blockIdsReversed);
        Collections.reverse(blockIds);

        // Extract edges between consecutive blocks that are connected in the CFG
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < blockIds.size() - 1; i++) {
            int fromId = blockIds.get(i);
            int toId = blockIds.get(i + 1);

            CfgBlockState fromBlock = blocks.get(fromId);
            if (fromBlock != null && fromBlock.successorIds.contains(toId)) {
                edges.add(new int[]{fromId, toId});
            }
        }

        return edges;
    }

    /**
     * Record the edges taken by a completed execution path.
     * Updates the runtime coverage tracking.
     */
    public void recordPathEdges(List<int[]> edges) {
        for (int[] edge : edges) {
            int fromId = edge[0];
            int toId = edge[1];
            CfgBlockState block = blocks.get(fromId);
            if (block != null) {
                boolean isNew = block.coveredEdges.add(toId);
                if (isNew) {
                    logger.finest("[CfgTracker] New edge covered: " + fromId + " -> " + toId);
                }
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
    public boolean areAllEdgesCovered(List<int[]> edges) {
        if (edges.isEmpty()) {
            // No edges found - can't determine coverage, treat as not covered
            return false;
        }

        for (int[] edge : edges) {
            int fromId = edge[0];
            int toId = edge[1];
            CfgBlockState block = blocks.get(fromId);
            if (block == null || !block.coveredEdges.contains(toId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the weight for a block. Used by the priority queue.
     * Returns 0 if the block has any uncovered outgoing edges (high priority).
     * Returns 1 if all outgoing edges are covered (low priority).
     */
    public double getWeight(int blockId) {
        CfgBlockState block = blocks.get(blockId);
        if (block == null) {
            // Unknown block - treat as uncovered (high priority)
            return 0;
        }
        return block.isFullyCovered() ? 1 : 0;
    }

    /**
     * Internal state for a single CFG block.
     */
    static class CfgBlockState {
        final int id;
        final Set<Integer> successorIds;
        final Set<Integer> coveredEdges;
        final boolean initiallyCovered;
        boolean visited;

        CfgBlockState(int id, Set<Integer> successorIds, boolean initiallyCovered) {
            this.id = id;
            this.successorIds = successorIds;
            this.coveredEdges = new HashSet<>();
            this.initiallyCovered = initiallyCovered;
            this.visited = initiallyCovered;
        }

        /**
         * A block is fully covered if all its outgoing edges are covered.
         * A leaf block (no successors) is fully covered if it has been visited.
         */
        boolean isFullyCovered() {
            if (successorIds.isEmpty()) {
                return visited;
            }
            return coveredEdges.containsAll(successorIds);
        }
    }
}
