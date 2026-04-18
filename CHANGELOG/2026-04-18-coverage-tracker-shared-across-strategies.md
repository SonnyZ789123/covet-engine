# Refactor: shared coverage tracker across exploration strategies

**Date:** 2026-04-18

## Motivation

`CfgCoverageTracker` was owned by `CoverageHeuristicStrategy`, so DFS/BFS runs
had no branch-coverage telemetry, no IGNORE filtering, and could not be used
with `BranchCoverageTermination`. To compare strategies fairly the tracker has
to be available regardless of which exploration strategy is active.

## What changed

- `ConcolicConfig` now holds an optional `CfgCoverageTracker` and an
  `ignoreCoveredPaths` flag, exposed via `getCoverageTracker()` and
  `shouldIgnoreCoveredPaths()`.
- The tracker is sourced one of two ways during `ConcolicConfig.initialize`:
  1. **Coverage heuristic (back-compat).** `CoverageHeuristicStrategy` loads
     the block map from its `coverage_heuristic.config` as before; the tracker
     and the existing `ignore_covered_paths` flag are then copied to
     `ConcolicConfig`.
  2. **Top-level JPF config (new).** When the strategy is anything else, the
     tracker is loaded from the JPF config keys:
     - `jdart.coverage.block_map_path` -- path to the block map JSON
     - `jdart.coverage.ignore_covered_paths` -- bool, defaults to `false`
- IGNORE detection (`checkCoveredPathOnCompletion`), runtime edge recording
  (`recordCompletedPathEdges`), and the `jdart.evaluation` progress log all
  moved into `ConcolicMethodExplorer`. They now run for any strategy as long
  as a tracker is available.
- `BranchCoverageTermination` now takes a `CfgCoverageTracker` instead of a
  `CoverageHeuristicStrategy`, so it works under DFS/BFS too.
- `CoverageHeuristicStrategy` lost its now-redundant `recordCompletedPath`,
  `getBranchCoveragePercentage`, and inline evaluation logging; it exposes
  `getCoverageTracker()` so the config can pick it up.

## Files changed

- `src/main/gov/nasa/jpf/jdart/config/ConcolicConfig.java` -- tracker/flag fields, JPF-config loader, wiring for `BranchCoverageTermination`
- `src/main/gov/nasa/jpf/jdart/ConcolicMethodExplorer.java` -- IGNORE check, edge recording, and `jdart.evaluation` log moved here, gated on the shared tracker
- `src/main/gov/nasa/jpf/jdart/exploration/CoverageHeuristicStrategy.java` -- expose tracker via `getCoverageTracker()`, remove the duplicated path-recording / logging code
- `src/main/gov/nasa/jpf/jdart/termination/BranchCoverageTermination.java` -- depend on `CfgCoverageTracker` instead of the strategy

## Verification

Same `RiskClassifier` SUT, three setups:

| strategy | block-map source | ignore | total paths | OK | IGNORE | term |
|----------|------------------|--------|-------------|----|--------|------|
| CoverageHeuristic | coverage_heuristic.config (back-compat) | true | 27 | 9 | 18 | branch >= 95% |
| DFS | jdart.coverage.block_map_path | false | 225 | 225 | 0 | branch >= 95% |
| DFS | jdart.coverage.block_map_path | true | 225 | 10 | 215 | branch >= 95% |

All three produce the `jdart.evaluation` curve from 62.14% to 95.71%.

## Notes

- IGNORE filtering is left optional per run because for fair strategy
  comparison you usually want every path counted.
- For DFS/BFS without `jdart.coverage.block_map_path` set, behavior is
  unchanged: no tracker, no telemetry, no IGNORE. `BranchCoverageTermination`
  in that case logs a one-time warning and never fires.
