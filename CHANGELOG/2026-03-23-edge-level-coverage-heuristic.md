# Edge-Level Coverage in Heuristic (2026-03-23)

## What changed

The coverage heuristic now uses **edge-level** (branch-level) coverage data from the `intellij-coverage-model` v3.1.0 `EdgeCoverageDTO`, replacing the previous block-level approximation.

### 1. Precise edge initialization (`CfgCoverageTracker`)

Previously, the tracker initialized edge coverage coarsely:
- COVERED blocks: all outgoing edges marked covered
- PARTIALLY_COVERED blocks: no edges marked covered

Now it reads the actual `EdgeCoverageDTO` list per block. Each edge with `hits > 0` is marked covered individually. This correctly handles partially-covered blocks where some branches are covered and others are not.

Falls back to old behavior when edge data is absent (backward compatible with v3.0.x block maps).

### 2. Edge-level exploration guiding (`CoverageHeuristicStrategy`)

Previously, the priority queue weighted children by the **target block's** overall coverage (does it have any uncovered edges?).

Now it weights children by the **specific edge** from the decision block to the child's target block. At an `if` statement in block X with branches to blocks Y and Z, the weight is based on whether edge X->Y or X->Z is covered -- directly prioritizing the uncovered branch.

When JDart's execution tree diverges from the static CFG (e.g., loop unrolling), the edge weight falls back to block-level weight.

### 3. New `getEdgeWeight()` method (`CfgCoverageTracker`)

Returns coverage weight for a specific edge (from -> to), with fallback to block-level weight when the edge doesn't exist in the static CFG.

### 4. New `getBranchInstruction()` getter (`DecisionData`)

Exposes the branch instruction at a decision point, needed by the strategy to determine the "from" block.

## Files modified

- `src/main/gov/nasa/jpf/jdart/exploration/coverage/CfgCoverageTracker.java`
- `src/main/gov/nasa/jpf/jdart/exploration/CoverageHeuristicStrategy.java`
- `src/main/gov/nasa/jpf/jdart/constraints/tree/DecisionData.java`
