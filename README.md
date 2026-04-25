# COVET Engine

The **COVET Engine** is the concolic execution component of the **COVET** test-generation pipeline. It runs a Java method with concrete inputs while tracking symbolic path constraints, negates a constraint after each path, and asks Z3 for new inputs until the path tree is exhausted or a budget runs out. From the recorded paths it generates a JUnit test suite (`assertEquals` for `OK` paths, `assertThrows` for `ERROR` paths).

It is built on top of NASA's [JDart][jdart] (concolic execution on Java PathFinder). The main thesis contribution layered on top is the **`CoverageHeuristicStrategy`**: a JDart exploration strategy that consumes a coverage block map produced by `pathcov` and steers exploration toward uncovered edges.

## Where it fits

```
pathcov  ──> icfg_block_map.json ──>  covet-engine  ──>  JUnit test suite
                                       (this repo)
```

The full pipeline (config generation, container orchestration, pathcov ⇄ covet-engine wiring) lives in the sibling `coverage-guided-concolic-pipeline` repo. This repository is just the engine.

## What can be symbolic

Only primitives induce branches that the engine explores: `int`, `long`, `short`, `byte`, `char`, `boolean`, `float`, `double`, primitive fields of objects, and elements of primitive arrays (length is fixed). Strings, user-defined classes, multi-dimensional arrays, and non-primitive object fields are always concrete. If the real method is not directly addressable through primitive parameters, target a thin primitive-only wrapper instead.

## Building and running

The engine requires Java 8 and a few legacy dependencies (jpf-core 8.0, jConstraints, jConstraints-z3, Z3 4.4.1), so it runs inside a Docker image (`sonnyz789123/jdart-image:<version>`). The local `covet-engine` source is bind-mounted into the container during development.

```bash
# Enter the container
docker exec -it jdart /bin/bash

# Compile (the in-container path is still /jdart-project/jdart for now)
cd /jdart-project/jdart && ant

# Run on a SUT
/jdart-project/jpf-core/bin/jpf /configs/sut.jpf
```

`sut.jpf` is bind-mounted from the host. The container, image, and config generation are managed by the `coverage-guided-concolic-pipeline` orchestrator.

## Configuration

The COVET Engine is driven by JPF-style `.jpf` property files. The minimum config:

```properties
@using = jpf-jdart

shell        = gov.nasa.jpf.jdart.JDart
symbolic.dp  = z3

target                  = features.simple.Input
concolic.method.bar     = features.simple.Input.bar(d:double)
concolic.method         = bar
```

The orchestrator generates a layered chain:

```
sut.jpf            <-- user-editable
  @includes jdart.jpf       <-- engine defaults (exploration, termination, solver)
  @includes sut_gen.jpf     <-- generated (target class, classpath, method)
```

### Exploration strategies

| Strategy | Class | Notes |
|----------|-------|-------|
| **CoverageHeuristic** | `gov.nasa.jpf.jdart.exploration.CoverageHeuristicStrategy` | Default. Reads the pathcov block map; primary sort = edge coverage weight, secondary = depth. |
| DFS | `gov.nasa.jpf.jdart.exploration.DFSStrategy` | Stock JDart depth-first. |
| BFS | `gov.nasa.jpf.jdart.exploration.BFSStrategy` | Stock JDart breadth-first. |

The coverage tracker (`CfgCoverageTracker`) is owned by `ConcolicConfig`, not by the strategy. The heuristic registers it from `coverage_heuristic.config`; DFS and BFS opt in via top-level keys:

```properties
jdart.coverage.block_map_path     = /data/blockmaps/icfg_block_map.json
jdart.coverage.ignore_covered_paths = true   # default false
```

When the tracker is present, three behaviours light up regardless of strategy:
- `jdart.evaluation` log line (`elapsed=Xms branch_coverage=Y%`) on every coverage change
- `IGNORE` filtering: paths whose branch decisions are all already covered are dropped from the test suite
- `BranchCoverageTermination` becomes available

Branch coverage is computed identically to `pathcov`'s `BranchCoverage.calculate(BlockMapDTO)`, so the threshold value matches what the pipeline reports.

### Termination strategies

Exactly one is selected via `jdart.termination=<class>,<int args...>`:

| Strategy | Args | Stops when |
|----------|------|------------|
| `NeverTerminate` | -- | All reachable paths exhausted (default). |
| `UpToFixedNumber` | `n` | After ~`n` completed paths. |
| `TimedTermination` | `h,m,s[,ms]` | Wall-clock budget exceeded. |
| `BranchCoverageTermination` | `pct` | Runtime branch coverage ≥ `pct`%. Needs a tracker. |
| `TimedOrBranchCoverageTermination` | `h,m,s,pct` | Whichever fires first. Coverage arm is skipped (warn-once) if no tracker is registered. |

## Test suite generation

For each `OK` path: a test that calls the target with the solved inputs and asserts the recorded return value. For each `ERROR` path: an `assertThrows` test for the recorded exception type. Generated tests cover **full execution paths** only — they do not check side effects, invariants, or post-conditions. Both JUnit 4 and JUnit 5 are supported (`jdart.tests.junit_version`); under JUnit 5 each test is `@Tag`-annotated with the structural hash of every CFG block it covers, which is what enables the block-diff selective re-run feature.

## Unsupported / limited

| Feature | Status |
|---------|--------|
| Recursion | Path tree branches infinitely; bound with `max_nesting_depth`. |
| Concurrency | Not supported. |
| External libraries | Not instrumented during test generation; classpath them in manually. |
| Unbounded loops | Path explosion; constrain inputs or set a time limit. |
| Generics | Symbolic generics are not expressible. |
| Lambda / reflection-heavy code | Partially broken (JPF limitation). |
| `java.awt` and similar JVM-heavy APIs | Not / partially modelled by JPF. |

## Determinism

Inherited from JPF: `Random` always returns 0, `System.identityHashCode` is fixed, and time is frozen at execution start. True non-determinism only enters via uninstrumented external calls.

## Heritage

The engine is a fork of [JDart][jdart], originally from NASA Ames / CMU. To cite the underlying tool:

> Kasper Luckow, Marko Dimjašević, Dimitra Giannakopoulou, Falk Howar, Malte Isberner, Temesghen Kahsai, Zvonimir Rakamarić, Vishwanath Raman, **JDart: A Dynamic Symbolic Analysis Framework**, TACAS 2016. \[[pdf](http://soarlab.org/publications/tacas2016-ldghikrr.pdf)\] \[[bibtex](http://soarlab.org/publications/tacas2016-ldghikrr.bib)\]

Most JDart concepts (`.jpf` config, the `gov.nasa.jpf.jdart.*` packages, JPF tooling) are inherited unchanged. The COVET-specific additions live under `gov.nasa.jpf.jdart.exploration` (`CoverageHeuristicStrategy`), `gov.nasa.jpf.jdart.termination` (`BranchCoverageTermination`, `TimedOrBranchCoverageTermination`), and the `CfgCoverageTracker` plumbing on `ConcolicConfig`.

[jdart]: https://github.com/psycopaths/jdart
