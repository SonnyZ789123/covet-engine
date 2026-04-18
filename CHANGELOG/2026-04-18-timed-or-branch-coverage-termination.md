# Feature: TimedOrBranchCoverageTermination

**Date:** 2026-04-18

## Motivation

Evaluation runs want a safety timeout in case the coverage threshold is
unreachable (e.g. DFS/BFS on a large SUT) while still stopping early when
branch coverage saturates. Running only `TimedTermination` wastes wall-clock
time after coverage plateaus; running only `BranchCoverageTermination` risks
hanging when the threshold cannot be met.

## What changed

New termination plugin that stops on whichever condition fires first:

```
jdart.termination = gov.nasa.jpf.jdart.termination.TimedOrBranchCoverageTermination,H,M,S,PCT
```

Four int constructor arguments: hours, minutes, seconds, branch-coverage
threshold percent. Wired to the shared `CfgCoverageTracker` the same way as
`BranchCoverageTermination` (via `ConcolicConfig`). Without a tracker the
coverage arm is skipped (warn-once) and only the time budget applies, so it
still works under DFS/BFS if the user forgets to set `jdart.coverage.block_map_path`.

`getReason()` reports which arm fired: `"Time limit expired (X ms)"` or
`"Branch coverage threshold reached: X% >= N%"`.

## Files changed

- `src/main/gov/nasa/jpf/jdart/termination/TimedOrBranchCoverageTermination.java` (new)
- `src/main/gov/nasa/jpf/jdart/config/ConcolicConfig.java` -- wire the tracker into the new class alongside `BranchCoverageTermination`
