# Fix: Coverage Heuristic IGNORE Detection and Exploration Guiding (2026-04-05)

## Problem

JDart v3.1.0 on FinScore8 (30s, 71% initial coverage): 3 OK / 3383 IGNORE. Coverage heuristic effectively broken for interprocedural SUTs. Three distinct bugs identified.

## Bug 1: Edge extraction used consecutive block pairs (cross-method failure)

`extractCfgEdges` built a sequence of block IDs from the constraints tree and checked consecutive pairs against `successorBlockIds`. The block map has 0 cross-method successor edges (718 within-method only), so cross-method transitions produced no valid edges. The extracted edge list was near-empty for interprocedural paths.

**Fix:** Rewrote `extractCfgEdges` to use per-decision extraction: for each decision node, get the parent's `branchInsn` (from-block) and find the child index via `findChildIndex`. Returns `(fromBlockId, branchIndex)` pairs instead of `(fromBlockId, toBlockId)`. This avoids the `toBlockId` line-number ambiguity that caused 42% of decisions to produce invalid edges ("notSuccessor").

## Bug 2: Branch index inversion (JDart vs block map)

The block map's `EdgeCoverageDTO.branchIndex` uses **source-level** convention:
- branchIndex 0 = IF_TRUE (source condition true = bytecode fall-through)
- branchIndex 1 = IF_FALSE (source condition false = bytecode jump taken)

JDart's child index uses **bytecode-level** convention:
- child 0 = bytecode jump taken = source false
- child 1 = bytecode fall-through = source true

Without conversion, the heuristic checked the WRONG branch's coverage at every if-statement, guiding exploration backwards.

**Fix:** Added `toJdartBranchIndex(EdgeCoverageDTO)` that maps IF_TRUE -> 1 and IF_FALSE -> 0 when loading initial coverage data. SWITCH branches are unchanged.

## Bug 3: Runtime tracking was too aggressive for priority queue

Two sub-issues:
- `block.visited = true` after first JDart visit prevented re-exploration of partially-covered blocks through different constraint paths
- Runtime-updated weights caused the priority queue to deprioritize blocks that JDart had visited once but still had uncovered branches

**Fix:** Priority queue weight uses `block.initiallyCovered` (static, matching v2.5.2). Blocks not fully covered by the initial test suite keep weight 0 permanently. Runtime branch tracking is kept for IGNORE detection only.

## Additional fix: pathcov line-number collision (not yet fixed)

`CoverageReport.buildLineToCoverageMap()` in pathcov uses a flat `Map<Integer, LineDTO>` keyed by line number across ALL methods. 51.6% of lines have collisions, causing 143 edges (19.9%) to get `hits=-1` (unresolvable). Fix: scope the map per method. This is a pathcov bug, tracked separately.

## Results on FinScore8 (30s)

| Version | Initial | Final | Improvement | OK paths | IGNORE |
|---------|---------|-------|-------------|----------|--------|
| v2.5.2 | 71.77% | 77.91% | +6.14% | 1690 | 1 |
| v3.1.0 (broken) | 71.06% | ~71% | ~0% | 3 | 3383 |
| **v3.1.0 (fixed)** | 71.06% | **77.21%** | **+6.15%** | 2885 | 1 |

First 1000 tests achieve 77.21% (same as all tests) - heuristic frontloads coverage-improving paths.

## Files changed

- `CfgCoverageTracker.java`: rewrote `extractCfgEdges`, added `toJdartBranchIndex`, separated `initiallyCoveredBranches`/`runtimeCoveredBranches`, static `getWeight`
- `CoverageHeuristicStrategy.java`: `addChildren` uses target-block weight (like v2.5.2), `pathIsBlockCovered` uses branch-index IGNORE detection
- `TestSuite.java`: fixed sub-suite naming to match JUnit discovery pattern
