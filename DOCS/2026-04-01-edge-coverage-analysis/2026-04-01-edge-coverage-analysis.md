# Coverage Heuristic Analysis: `EdgeCoverage.foo(int x, int y, int z)`

## 1. SUT

### 1.1 Source Code

```java
package com.kuleuven.claude;

public class EdgeCoverage {

    public static void main(String[] args) {
        foo(1, 1, 1);
    }

    public static int foo(int x, int y, int z) {
        if (x > 0) {
            // x > 0
            if (y > 0) {
                // x > 0 && y > 0
                if (z > 0) {
                    // x > 0 && y > 0 && z > 0
                    return 0;
                } else {
                    // x > 0 && y > 0 && z <= 0
                    return 1;
                }
            } else {
                // x > 0 && y <= 0
                if (z > 0) {
                    // x > 0 && y <= 0 && z > 0
                    return 2;
                } else {
                    // x > 0 && y <= 0 && z <= 0
                    return 3;
                }
            }
        } else {
            // x <= 0
            if (y > 0) {
                // x <= 0 && y > 0
                if (z > 0) {
                    // x <= 0 && y > 0 && z > 0
                    return 4;
                } else {
                    // x <= 0 && y > 0 && z <= 0
                    return 5;
                }
            } else {
                // x <= 0 && y <= 0
                if (z > 0) {
                    // x <= 0 && y <= 0 && z > 0
                    return 6;
                } else {
                    // x <= 0 && y <= 0 && z <= 0
                    return 7;
                }
            }
        }
    }
}
```

A perfect binary tree: 3 nested `if-else` decisions over 3 parameters, producing 8 distinct return values (0-7). Each path is uniquely determined by the sign of `(x, y, z)`.

### 1.2 Test Suite (Initial)

**No test suite exists.** Initial edge coverage is **0%**. The `main()` method calls `foo(1, 1, 1)` which serves as the initial concrete execution seed for JDart.

### 1.3 Generated Test Suite (JDart Output)

```java
@Test public void test0() { assertEquals(7, EdgeCoverage.foo(-2147483647, 0, 0)); }
@Test public void test1() { assertEquals(6, EdgeCoverage.foo(-2147483646, -2147483647, 1)); }
@Test public void test2() { assertEquals(5, EdgeCoverage.foo(-2147483646, 1, 0)); }
@Test public void test3() { assertEquals(4, EdgeCoverage.foo(-2147483646, 1, 1)); }
@Test public void test4() { assertEquals(3, EdgeCoverage.foo(2, -2147483647, 0)); }
@Test public void test5() { assertEquals(2, EdgeCoverage.foo(2, -2147483647, 1)); }
@Test public void test6() { assertEquals(1, EdgeCoverage.foo(2, 1, -2147483647)); }
@Test public void test7() { assertEquals(0, EdgeCoverage.foo(1, 1, 1)); }
```

All 8 paths explored, 0 errors, 0 DONT_KNOW, 0 IGNORE.

### 1.4 CFG Structure

The block map (`icfg_block_map.json`) confirms 15 nodes (0-14) numbered in pre-order traversal:

```
                         Node 0: if(x > 0)
                         [line 10, branches 0/2]
                       /                        \
                  1: x>0                      0: x<=0
                    /                              \
           Node 1: if(y > 0)              Node 8: if(y > 0)
           [line 12, branches 0/2]        [line 33, branches 0/2]
           /              \                /              \
      1: y>0          0: y<=0         1: y>0          0: y<=0
        /                \              /                \
  Node 2: if(z > 0)  Node 5: if(z > 0)  Node 9: if(z > 0)  Node 12: if(z > 0)
  [line 14]           [line 23]          [line 35]           [line 44]
   /       \           /       \          /       \           /        \
1:z>0   0:z<=0     1:z>0   0:z<=0      1:z>0   0:z<=0      1:z>0    0:z<=0
  |       |         |       |          |       |          |         |
 [3]     [4]       [6]     [7]       [10]    [11]       [13]      [14]
ret 0   ret 1     ret 2   ret 3     ret 4   ret 5     ret 6     ret 7
```

**Edge inventory** (14 total, from block map edges):

| Source | Target | branchIndex | branchType | Condition |
|--------|--------|-------------|------------|-----------|
| 0 | 1 | 1 | IF_FALSE | x > 0 |
| 0 | 8 | 0 | IF_TRUE | x <= 0 |
| 1 | 2 | 1 | IF_FALSE | y > 0 |
| 1 | 5 | 0 | IF_TRUE | y <= 0 |
| 2 | 3 | 1 | IF_FALSE | z > 0 |
| 2 | 4 | 0 | IF_TRUE | z <= 0 |
| 5 | 6 | 1 | IF_FALSE | z > 0 |
| 5 | 7 | 0 | IF_TRUE | z <= 0 |
| 8 | 9 | 1 | IF_FALSE | y > 0 |
| 8 | 12 | 0 | IF_TRUE | y <= 0 |
| 9 | 10 | 1 | IF_FALSE | z > 0 |
| 9 | 11 | 0 | IF_TRUE | z <= 0 |
| 12 | 13 | 1 | IF_FALSE | z > 0 |
| 12 | 14 | 0 | IF_TRUE | z <= 0 |

Note on branchType: The Java source `if (x > 0)` compiles to bytecode `ifle target` ("if x <= 0, jump"). So `IF_TRUE` (branchIndex=0) = the jump condition `x <= 0` is true (branch taken), and `IF_FALSE` (branchIndex=1) = fall-through (`x > 0`). All coverage data shows 0 hits -- every block is `NOT_COVERED`.

## 2. Coverage Heuristic Strategy

### 2.1 Order of Execution

| # | Solver Target | Valuation | Executed Path | New Edges (CfgTracker) | Cumul. Edges |
|---|--------------|-----------|---------------|----------------------|-------------|
| 0 | `[]` (initial) | `x=1, y=1, z=1` | `[1,1,1]` | 1->2, 2->3 | 2 |
| 1 | `[0]` | `x=-2147483647` | `[0,0,0]` | 8->12, 12->14 | 4 |
| 2 | `[0,1]` | `x=-2147483646, y=1` | `[0,1,0]` | 8->9, 9->11 | 6 |
| 3 | `[1,0]` | `x=2, y=-2147483647` | `[1,0,0]` | 1->5, 5->7 | 8 |
| 4 | `[0,1,1]` | `x=-2147483646, y=1, z=1` | `[0,1,1]` | 9->10 | 9 |
| 5 | `[1,0,1]` | `x=2, y=-2147483647, z=1` | `[1,0,1]` | 5->6 | 10 |
| 6 | `[0,0,1]` | `x=-2147483646, y=-2147483647, z=1` | `[0,0,1]` | 12->13 | 11 |
| 7 | `[1,1,0]` | `x=2, y=1, z=-2147483647` | `[1,1,0]` | 2->4 | 12 |

CfgTracker explicitly logged 12 new edges. Edges 0->1 and 0->8 are structurally covered by executions 0 and 1 respectively but not logged by CfgTracker (the root block's outgoing edges appear to be tracked implicitly).

### 2.2 Order Analysis

**Execution 0** -- Initial execution with `main()` seed `(1,1,1)`. Path `[1,1,1]`: `x>0, y>0, z>0 -> return 0`. Covers edges in the deepest-left branch of the x>0 subtree.

**findNext after execution 0:** The algorithm pops all 3 branches and extends with `branchIdx=0` (`x<=0`). This negates the root branch -- the shallowest unexplored branch. The solver picks `x=-2147483647` (the boundary value for `x <= 0`). Variables `y` and `z` are unconstrained by the target `[0]`, so the solver assigns default values `y=0, z=0`.

**Execution 1** -- Path `[0,0,0]`: `x<=0, y<=0, z<=0 -> return 7`. Covers the opposite corner of the binary tree (the "mirror" of the initial execution). After this, both root branches are explored.

**findNext after execution 1:** Pops all, extends `[0, 1]` -- keeps `x<=0` and negates `y` to `y>0`. The algorithm stays in the `x<=0` subtree and explores the other depth-1 branch. The solver picks `y=1, x=-2147483646`.

**Execution 2** -- Path `[0,1,0]`: `x<=0, y>0, z<=0 -> return 5`. Covers edges 8->9 and 9->11. Now both depth-1 branches in the `x<=0` subtree are explored.

**findNext after execution 2:** Pops all, extends `[1, 0]` -- flips to `x>0` and explores `y<=0`. The algorithm backtracks to the root and enters the `x>0` subtree's unexplored depth-1 branch. The solver picks `x=2, y=-2147483647`.

**Execution 3** -- Path `[1,0,0]`: `x>0, y<=0, z<=0 -> return 3`. Covers edges 1->5 and 5->7. Now all depth-1 branches across both subtrees are explored.

**Summary of depth-1 exploration:**

```
After execs 0-3, all 4 paths at depth <= 2 have been explored:
  [1,1,*]  [1,0,*]  [0,1,*]  [0,0,*]
  exec 0   exec 3   exec 2   exec 1

Remaining unexplored: the 4 "sibling" paths where z is flipped:
  [0,1,1]  [1,0,1]  [0,0,1]  [1,1,0]
```

**Executions 4-7** -- Each targets a depth-3 path (the full expected path matches the executed path), covering exactly 1 new edge per execution:

| Exec | Target | Flips z from | New Edge |
|------|--------|-------------|----------|
| 4 | `[0,1,1]` | exec 2's `z<=0` -> `z>0` | 9->10 |
| 5 | `[1,0,1]` | exec 3's `z<=0` -> `z>0` | 5->6 |
| 6 | `[0,0,1]` | exec 1's `z<=0` -> `z>0` | 12->13 |
| 7 | `[1,1,0]` | exec 0's `z>0` -> `z<=0` | 2->4 |

The algorithm alternates between the `x<=0` and `x>0` subtrees: `[0,1,1]` -> `[1,0,1]` -> `[0,0,1]` -> `[1,1,0]`. This is the coverage heuristic's backtracking strategy traversing the concolic tree.

### 2.3 Edge Coverage Progression

```
Exec 0  ||||..........  14.3%   (2 new edges)
Exec 1  ||||||||......  28.6%   (2 new edges)
Exec 2  ||||||||||||..  42.9%   (2 new edges)
Exec 3  ||||||||||||||  57.1%   (2 new edges)
Exec 4  |||||||||||||||  64.3%   (1 new edge)
Exec 5  ||||||||||||||||  71.4%   (1 new edge)
Exec 6  |||||||||||||||||  78.6%   (1 new edge)
Exec 7  ||||||||||||||||||  85.7%   (1 new edge)
```

(12 of 14 edges explicitly tracked. Including the implicit root edges 0->1 and 0->8, total is 14/14 = 100%.)

### 2.4 Key Observations

**1. With 0% initial coverage, the coverage heuristic has no covered paths to deprioritize.** In the reference analysis (`test.heuristic.Test.foo`), the test suite covered paths for `x=0,7,8,9,10`, and the heuristic's power was demonstrated by exploring uncovered paths first and leaving the already-covered paths (`x=7,8,9`) to the end. Here, every path is equally uncovered, so the heuristic provides no prioritization advantage over BFS.

**2. No paths are marked as IGNORE.** In the reference analysis with `ignore_covered_paths=true`, fully-covered paths would be skipped. With 0% coverage, no path can be ignored -- all 8 paths must be explored.

**3. Every execution covers at least 1 new edge.** No execution is "wasted" on an already-covered path. This is the best-case scenario for any exploration strategy on this SUT.

**4. The exploration follows a depth-first backtracking pattern:**
- First, it explores both root branches (depth 0)
- Then, it explores both depth-1 branches in each subtree
- Finally, it flips depth-2 branches to cover the remaining leaf paths

The solver targets progress by depth: `[0]` (depth 1) -> `[0,1]`, `[1,0]` (depth 2) -> `[0,1,1]`, `[1,0,1]`, `[0,0,1]`, `[1,1,0]` (depth 3).

**5. The tree structure is ideal for concolic execution.** A perfect binary tree with independent boolean conditions (`x>0`, `y>0`, `z>0`) means every path is satisfiable and the solver always returns SAT. No UNSAT or DONT_KNOW paths. The constraints tree confirms 8/8 OK paths with average path length 4.0.

**6. Comparison with the reference analysis:** The reference SUT had 1 parameter, `switch` statements creating multi-way branches, and a test suite covering 5 of 15 paths. This SUT has 3 parameters, only binary branches, and 0 initial coverage. The reference better showcases the heuristic's value because partial coverage creates a meaningful priority difference. This SUT would serve better as a **baseline** -- it demonstrates the heuristic's behavior when it degenerates to structural exploration.

## 3. JDart Output

### 3.1 Constraints Tree

```
-('x' <= 0)
  |-[+]-('y' <= 0)
  |      |-[+]-('z' <= 0)
  |      |      |-[+]_/OK: [  ]
  |      |      +-[-]_/OK: [  ]
  |      +-[-]-('z' <= 0)
  |            |-[+]_/OK: [  ]
  |            +-[-]_/OK: [  ]
  +-[-]-('y' <= 0)
        |-[+]-('z' <= 0)
        |      |-[+]_/OK: [  ]
        |      +-[-]_/OK: [  ]
        +-[-]-('z' <= 0)
              |-[+]_/OK: [  ]
              +-[-]_/OK: [  ]
```

### 3.2 Statistics

| Metric | Value |
|--------|-------|
| Total paths | 8 |
| OK paths | 8 |
| ERROR paths | 0 |
| DONT_KNOW paths | 0 |
| IGNORE paths | 0 |
| Average path length | 4.0 |
| Total valuations | 8 |
| JDart execution time | 532 ms |
| JPF boot time | 354 ms |
