# Fix: IGNORE Path Detection for Interprocedural Execution (2026-04-05)

## Problem

JDart v3.1.0 marked nearly all execution paths as IGNORE on complex, multi-method SUTs. On FinScore8 (30s, 58.96% initial coverage): **3383 IGNORE / 3 OK** out of 3426 total paths. The coverage heuristic was effectively broken for any interprocedural SUT.

## Root Cause

The v3.1.0 `pathIsBlockCovered` used `CfgCoverageTracker.extractCfgEdges()` + `areAllEdgesCovered()` to decide IGNORE. This approach had two compounding flaws:

**1. The block map has 0 cross-method successor edges.**

The ICFG block map (from pathcov) includes blocks from multiple methods, but `successorBlockIds` only references blocks within the same method. Confirmed: 718 within-method edges, 0 cross-method edges.

`extractCfgEdges` walks the constraints tree and checks consecutive block pairs against `successorIds`. For cross-method transitions (which dominate interprocedural paths), consecutive blocks are in different methods, so `fromBlock.successorIds.contains(toBlockId)` fails. The extracted edge list is nearly empty.

**2. Runtime tracking caused cascading false IGNORE.**

After each execution, `recordCompletedPath` recorded edges as covered. After ~3 OK paths, the few within-method edges that `extractCfgEdges` could find were all recorded as covered. `areAllEdgesCovered` on a near-empty list returned `true` for all subsequent paths.

## Fix

Reverted `pathIsBlockCovered` to the proven v2.5.2 approach:
- Walk each instruction along the constraints tree path
- Check its block coverage state against the **initial** (static) block map
- Conservative: any unmapped method or uncovered block -> NOT IGNORE

Kept `CfgCoverageTracker` for exploration guiding (edge-level weights in the priority queue). The tracker correctly falls back to block-level weights for cross-method edges.

## Results (FinScore8, 30s, same SUT)

| Metric | Before (bug) | After (fix) | v2.5.2 baseline |
|--------|-------------|-------------|-----------------|
| Initial coverage | 58.96% | 58.96% | 71.77% |
| Final coverage | ~59% | **63.64%** | 77.91% |
| OK paths | 3 | **4256** | 1690 |
| IGNORE paths | 3383 | **1** | 1 |
| Total paths | 3426 | **4350** | 1702 |

## Additional Fix: Test Suite Naming

Generated test class names (e.g., `FooTest0`) didn't match JUnit Platform's default discovery pattern `.*Tests?$`. This caused generated tests to be invisible to `--scan-classpath`. Fixed by inserting the index before "Test" suffix: `Foo0Test`.

## Files Changed

- `src/main/gov/nasa/jpf/jdart/exploration/CoverageHeuristicStrategy.java` - reverted `pathIsBlockCovered`
- `src/main/gov/nasa/jpf/jdart/testsuites/TestSuite.java` - fixed sub-suite naming

## Architecture Note

For the IGNORE check to use runtime edge tracking correctly, the block map would need cross-method successor edges (call/return edges in the ICFG). This is a pathcov change. Until then, the static block-level IGNORE check is conservative and correct.
