# Edge-Level Coverage Heuristic Analysis (2026-03-29)

Same SUT and test suite as the [5/12] node-based analysis, re-run with the new edge-level coverage heuristic.

## 1. SUT

```java
package com.kuleuven.claude;

public class HeuristicTest {

    public static int foo(int x) {

        if (x < 5) {
            if (x < 3) {
                switch (x) {
                    case 0: return 0;
                    case 1: return 1;
                    default: return 2;
                }
            } else {
                switch (x)  {
                    case 3: return 3;
                    case 4: return 4;
                    default: return x;
                }
            }
        } else if (x < 10) {
            if (x != 7) {
                if (x > 7) {
                    switch (x) {
                        case 8: return 8;
                        case 9: return 9;
                        default: return x;
                    }
                } else {
                    switch (x) {
                        case 5: return 5;
                        case 6: return 6;
                        default: return x;
                    }
                }
            } else {
                return 7;
            }
        } else {
            if (x == 10) {
                return 10;
            } else if (x == 11) {
                return 11;
            } else if (x == 12) {
                return 12;
            } else if (x == 13) {
                return 13;
            } else if (x == 14) {
                return 14;
            }
            return -1;
        }
    }

    public static void main(String[] args) {
        foo(5);
    }
}
```

Test suite covers x=0, 7, 8, 9, 10. Initial branch coverage from pathcov: **46.67%**

## 2. Initial Edge Coverage (from pathcov block map)

The edge-level data from `EdgeCoverageDTO` shows precise per-edge coverage. Key partially-covered blocks:

| Block | Description | Covered Edges | Uncovered Edges |
|-------|-------------|---------------|-----------------|
| 1 | `x < 3` | -> 2 (IF_FALSE, x>=3) | -> 6 (IF_TRUE, x<3) |
| 2 | `switch(x)` [0,1,default] | -> 3 (case 0) | -> 4 (case 1), -> 5 (default) |
| 12 | `x > 7` | -> 13 (IF_FALSE, x<=7) | -> 17 (IF_TRUE, x>7) |
| 13 | `switch(x)` [8,9,default] | -> 14 (case 8), -> 15 (case 9) | -> 16 (default) |
| 22 | `x == 10` | -> 23 (IF_FALSE, x==10) | -> 24 (IF_TRUE, x!=10) |

With the **old node-based** heuristic, PARTIALLY_COVERED blocks had NO edges pre-marked as covered (conservative). Now each edge is individually initialized from `EdgeCoverageDTO.hits`.

## 3. Edge-Level Heuristic: Order of Execution

| # | Valuation | Expected Path | Result | Notes |
|---|-----------|---------------|--------|-------|
| 0 | `x = 5` | `[0,1,1,0,0]` | OK | Initial execution (main calls `foo(5)`) |
| 1 | `x = 4` | `[1]` | OK | Uncovered: x<5 edge |
| 2 | `x = 0` | `[1,1]` | IGNORE | x=0 was in test suite |
| 3 | `x = 1` | `[1,1,1]` | OK | Uncovered: switch case 1 |
| 4 | `x = 2` | `[1,1,2]` | OK | Uncovered: switch default |
| 5 | `x = 3` | `[1,0,0]` | OK | Uncovered: x>=3, switch case 3 |
| - | - | `[1,0,2]` | UNSAT | x>=3, x<5, not 3 or 4: impossible |
| 6 | `x = 12` | `[0,0]` | OK | Uncovered: x>=10, x!=10 |
| 7 | `x = 10` | `[0,0,1]` | IGNORE | x=10 was in test suite |
| 8 | `x = 7` | `[0,1,0]` | IGNORE | x=7 was in test suite |
| 9 | `x = 11` | `[0,0,0,1]` | OK | Uncovered: x==11 |
| 10 | `x = 14` | `[0,0,0,0,0]` | OK | Uncovered: deep chain x!=10,11,12 |
| 11 | `x = 13` | `[0,0,0,0,0,1]` | OK | Uncovered: x==13 |
| 12 | `x = 6` | `[0,1,1,0,1]` | OK | Uncovered: switch case 6 |
| - | - | `[0,1,1,0,2]` | UNSAT | x<=7, x!=7, not 5 or 6: impossible |
| 13 | `x = 8` | `[0,1,1,1]` | IGNORE | x=8 was in test suite |
| - | - | `[0,1,1,1,2]` | UNSAT | x>7, x<10, not 8 or 9: impossible |
| 14 | `x = 9` | `[0,1,1,1,1]` | IGNORE | x=9 was in test suite |
| - | - | `[0,0,0,0,0,0,0]` | UNSAT | x>=10, x<15, not 10-14: impossible |

## 4. Comparison with Old Heuristic

### Old heuristic order (node-based, from [5/12] analysis)

| # | Valuation | Expected Path |
|---|-----------|---------------|
| 0 | `x = 5` | `[0,1,1,0,0]` |
| 1 | `x = 4` | `[1]` |
| 2 | `x = 3` | `[1,0,0]` |
| 3 | `x = 10` | `[0,0]` |
| 4 | `x = 11` | `[0,0,0]` |
| 5 | `x = 12` | `[0,0,0,0]` |
| 6 | `x = 14` | `[0,0,0,0,0]` |
| 7 | `x = 13` | `[0,0,0,0,0,1]` |
| 8 | `x = 0` | `[1,1]` |
| 9 | `x = 1` | `[1,1,1]` |
| 10 | `x = 2` | `[1,1,2]` |
| 11 | `x = 6` | `[0,1,1,0,1]` |
| 12 | `x = 7` | `[0,1,0]` |
| 13 | `x = 9` | `[0,1,1,1]` |
| 14 | `x = 8` | `[0,1,1,1,0]` |

### Key Differences

**1. IGNORE detection (5 paths skipped)**

The old heuristic explored all 15 paths, generating 15 test valuations. The new heuristic detects 5 paths (x=0, 7, 8, 9, 10) as already covered by the existing test suite and marks them IGNORE. Only **10 new valuations** are generated.

**2. Exploration order changes**

The first two paths are identical: `x=5` (initial), `x=4` (uncovered x<5 edge). After that the order diverges:

- **New heuristic**: After `x=4`, immediately dives into the x<3 subtree (x=0 IGNORE, x=1, x=2), then x>=3 (x=3), completing the entire x<5 subtree before moving to x>=5.
- **Old heuristic**: After `x=4`, explores x=3 first, then jumps to the x>=10 subtree (x=10, x=11, x=12...) before coming back to x<3 (x=0, x=1, x=2).

The edge-level weights cause more local exploration: once in a subtree, uncovered edges within it are prioritized before moving elsewhere.

**3. The x=10 "unlucky guess" problem is resolved**

The old [5/12] analysis noted: *"[0,0] is picked as next target, and the solver picks x=10 as value. Which is an unlucky guess because that path is already tested by the test suite."*

With the old node-based heuristic, the block for `x == 10` was marked COVERED (weight 1), same as the `x != 10` continuation. The queue couldn't distinguish them.

With edge-level weights, the edge to `x == 10` (block 22 -> 23, IF_FALSE) has `hits=1` (weight 1, covered), while the edge to `x != 10` (block 22 -> 24, IF_TRUE) has `hits=0` (weight 0, uncovered). The heuristic prioritizes the uncovered edge.

When x=10 is eventually explored (path #7), it's correctly detected as IGNORE and no test is generated.

**4. Already-tested paths: all correctly identified**

| Path | Old Heuristic | New Heuristic |
|------|--------------|---------------|
| x=0 | OK (path #8) | IGNORE (path #2) |
| x=7 | OK (path #12) | IGNORE (path #8) |
| x=8 | OK (path #14) | IGNORE (path #13) |
| x=9 | OK (path #13) | IGNORE (path #14) |
| x=10 | OK (path #3) | IGNORE (path #7) |

## 5. Statistics

| Metric | BFS (from [5/12]) | Old node-based (from [5/12]) | New edge-level |
|--------|-------------------|------------------------------|----------------|
| Total paths explored | 15 | 15 | 15 |
| OK paths (new tests) | 15 | 15 | 10 |
| IGNORE paths | 0 | 0 | 5 |
| Test valuations generated | 15 | 15 | 10 |
| Explored already-tested paths as OK | 5 | 5 | 0 |

## 6. Constraint Tree

```
-('x' >= 5)
  |-[+]-('x' >= 10)
  |      |-[+]-('x' != 10)
  |      |      |-[+]-('x' != 11)
  |      |      |      |-[+]-('x' != 12)
  |      |      |      |      |-[+]-('x' != 13)
  |      |      |      |      |      |-[+]_/OK: [  ]       x=14
  |      |      |      |      |      +-[-]_/OK: [  ]       x=13
  |      |      |      |      +-[-]_/OK: [  ]              x=12
  |      |      |      +-[-]_/OK: [  ]                     x=11
  |      |      +-[-]_/IGNORE                              x=10 (test suite)
  |      +-[-]-('x' == 7)
  |            |-[+]_/IGNORE                               x=7  (test suite)
  |            +-[-]-('x' <= 7)
  |                  |-[+]-('x' == 5)
  |                  |      |-[+]_/OK: [  ]                x=5
  |                  |      +-[-]_/OK: [  ]                x=6
  |                  +-[-]-('x' == 8)
  |                        |-[+]_/IGNORE                   x=8  (test suite)
  |                        +-[-]_/IGNORE                   x=9  (test suite)
  +-[-]-('x' >= 3)
        |-[+]-('x' == 3)
        |      |-[+]_/OK: [  ]                            x=3
        |      +-[-]_/OK: [  ]                            x=4
        +-[-]-('x' == 0)
              |-[+]_/IGNORE                                x=0  (test suite)
              +-[-]-('x' == 1)
                    |-[+]_/OK: [  ]                        x=1
                    +-[-]_/OK: [  ]                        x=2
```

---

## Appendix: Previous Node-Based Analysis ([5/12])

> The following is the original analysis of the node-based coverage heuristic from 5/12, preserved for reference. This was the baseline that the edge-level heuristic improves upon.

### Order Analysis (node-based heuristic)

We start with executing with the initial value of `x=5` which does not expose uncovered nodes at depth 1. The only unexplored open node is `if x >= 3` and that one is covered. Following just the order of the queue (currently like BFS) we choose `[1]` as our next target (`[0]` was also an option) and the solver chooses `x=4` as value. After execution we add the children of `[1]` to the prioritised queue, which are the 2 switch-cases `[1,1]` and `[1,0]`. Because `[1,0]` is uncovered, the current queue looks like:

```
{[1,0], [0], [1,1]}
```

`[0]` has a lower depth 1 as `[1,1]` so it has a higher priority, and `[1,0]` has a lower weight 0 than `[0]` and `[1,1]` (weight 1) because it's uncovered. We take `[1,0]` from the queue, but because it's already explored by the previous run (`x=4`) we add its children to the queue and try again. The queue is now:

```
{[1,0,0], [1,0,1], [1,0,2], [0], [1,1]}
```

Notice that we also add `[1,0,1]` which is the full path of the `x=4` execution. We don't check where the run to `[1,0]` (`x=4`) ended up. All 3 switch-case paths are uncovered, so they are put in front of the queue.

Notice that we still consider and prioritise uncovered paths that are already explored. If we would not prioritise explored paths, even if they are uncovered, we wouldn't have found the uncovered branches. It is imperative to keep doing this to ensure correct execution of the exploration algorithm. The second prioritisation scheme on depth will make sure that uncovered nodes with a lower depth will be put first.

Because we put the child nodes from left to right, we choose `[1,0,0]` as our next target and the solver picked `x=3` as value.

After exploring the x<5 subtree, the next node picked is `[0]`, but this node is already explored by the first execution `x=5`, and that node has children so we add them to the queue:

```
{[0,0], [0,1], [1,1]}
```

Here all nodes are covered and on the same depth, so it's indeterministic how these will be ordered. Based on the logs we see that `[0,0]` is picked as next target, and the solver picks `x=10` as value. **This is an unlucky guess because that path is already tested by the test suite.** After the execution we add the children:

```
{[0,0,0], [0,1], [1,1], [0,0,1]}
```

Here `[0,0,0]` is put in front because this is the only uncovered node, and `[0,0,1]` at the back because it's covered and has the highest depth. Because of the prioritisation the algorithm picks `[0,0,0]` as next target node, and the solver computes `x=11` as value. This chain of "target node's children are uncovered and targeted the next iteration" continues until the whole subtree is explored.

**Notice that the paths for x=7,8,9 are last, which were already tested in the test suite.** The node-based heuristic could not detect this and generated redundant tests for them.
