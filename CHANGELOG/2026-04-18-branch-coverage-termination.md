# Feature: Branch-coverage threshold termination strategy

**Date:** 2026-04-18

## Motivation

With the coverage heuristic, JDart kept exploring even after additional paths
could no longer push branch coverage higher. The pipeline already produces a
block map with branch hit data per line, and `CfgCoverageTracker` updates that
state every time a path completes -- so we have enough information to stop the
explorer once a target branch coverage is reached and move on to test-suite
generation earlier.

## What changed

New plugin `gov.nasa.jpf.jdart.termination.BranchCoverageTermination` taking a
single int constructor argument (threshold percent, 0-100). Configure in
`sut.jpf`:

```
jdart.termination = gov.nasa.jpf.jdart.termination.BranchCoverageTermination,80
```

After every completed path the explorer's `hasMoreChoices()` calls `isDone()`,
which queries `CfgCoverageTracker.getBranchCoveragePercentage()` and stops
JDart once it reaches the threshold. Logs `Branch coverage: X% (target N%)`
whenever the value changes so progress is visible.

The strategy only fires when the exploration strategy is
`CoverageHeuristicStrategy`. With DFS/BFS it logs a one-time warning and never
terminates, so existing strategies are unaffected.

## Wiring

`ConcolicConfig.parseTerminationStrategy` only accepts int constructor args.
Rather than rework the parsing, the wiring happens after both strategies are
parsed in `ConcolicConfig.finalizeConfig`:

```java
if (this.termination instanceof BranchCoverageTermination
        && this.explorationStrategy instanceof CoverageHeuristicStrategy) {
    ((BranchCoverageTermination) this.termination)
            .setCoverageStrategy((CoverageHeuristicStrategy) this.explorationStrategy);
}
```

## Aligning the metric with pathcov

JDart's metric must match pathcov's `BranchCoverage.calculate(BlockMapDTO)` so
that the threshold value the user sets matches what they see reported. Two
fixes were needed:

1. **Use line-summary fields, not edge counts.** pathcov's denominator is the
   sum of `line.branches.total` (an IntelliJ `BranchData` count), not the
   number of conditional `EdgeCoverageDTO`s. On the `RiskClassifier` block map
   that's 140 vs 44 -- counting edges underrepresents the denominator by ~3x.

2. **Match pathcov's per-block-line summing.** pathcov sums `line.branches`
   per `(block, line entry)`, and the same source line appears in multiple
   blocks (e.g. `classifyRisk` line 56 has multiplicity 3, contributing 6 to
   the total instead of 2). When JDart covers a previously-uncovered
   conditional edge, the numerator must increase by that line's multiplicity,
   not by 1. `CfgCoverageTracker` now records `(method, line)` multiplicities
   at init and the source line of each conditional branch block, then credits
   the multiplicity at query time.

### Known small misalignment (~1-2%)

The block's "branch source line" is taken to be the first line entry with
`branches.total > 0`. For blocks whose branching bytecode spans multiple
source lines (compound conditions split over lines), only one line gets
credited. On `RiskClassifier` JDart terminates at 80.00% (its metric) where
pathcov re-running on the generated tests reports 81.43%. Acceptable for a
termination signal -- it just means the threshold corresponds to "JDart's
metric", which slightly under-counts compared to a fresh pathcov run. To
tighten further, store the *set* of branch-source lines per block and sum
their multiplicities.

## Files changed

- `src/main/gov/nasa/jpf/jdart/termination/BranchCoverageTermination.java` (new)
- `src/main/gov/nasa/jpf/jdart/exploration/coverage/CfgCoverageTracker.java` -- new `getBranchCoveragePercentage()` and supporting state (pathcov totals, line multiplicity, per-block branch-source line)
- `src/main/gov/nasa/jpf/jdart/exploration/CoverageHeuristicStrategy.java` -- expose `getBranchCoveragePercentage()`
- `src/main/gov/nasa/jpf/jdart/config/ConcolicConfig.java` -- wire the exploration strategy into the termination after both are parsed
